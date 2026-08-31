#!/usr/bin/env bash
#
# ALTEN AI Copilot — local development launcher.
#
#   ./dev.sh doctor    check prerequisites, change nothing
#   ./dev.sh setup     one-time: python venv, npm install, .env scaffold
#   ./dev.sh build     compile all 5 Spring services
#   ./dev.sh up        start databases + all 7 app processes
#   ./dev.sh up auth   start databases + just one service (see SERVICES below)
#   ./dev.sh down      stop app processes (databases keep running)
#   ./dev.sh down --db stop app processes AND databases
#   ./dev.sh status    what is up, what is listening
#   ./dev.sh logs      tail every log at once
#   ./dev.sh logs chat tail one service
#   ./dev.sh restart chat
#
# Design notes:
#  * Databases run in Docker (compose.yml); apps run natively so that Spring
#    devtools restart and ng serve HMR both keep working.
#  * Upload directories are passed as ABSOLUTE paths via -D. The properties
#    files use CWD-relative defaults (app.upload.dir=uploads,
#    document.upload.path=uploads/documents), which means the upload location
#    silently depends on where you launched from. Overriding here pins
#    everything to <repo>/uploads regardless of CWD, without editing configs.

set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT/.dev/logs"
PID_DIR="$ROOT/.dev/pids"
VENV="$ROOT/rag-service/.venv"
ENV_FILE="$ROOT/.env"

# Load the root .env and export every value, so all five Spring services and
# the rag-service resolve the same ${JWT_SECRET}, ${POSTGRES_PASSWORD} etc.
# Docker Compose reads the same file on its own.
# Variables already present in the environment win over the file, so
#   LLM_MODEL=gpt-4o ./dev.sh restart rag
# works for one-off overrides. This matches rag-service's config.py, which loads
# the same file with override=False.
load_env() {
  [ -f "$ENV_FILE" ] || return 0
  local line key
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|'#'*) continue ;; esac
    case "$line" in *=*) ;; *) continue ;; esac
    key="${line%%=*}"
    key="${key#export }"
    key="$(printf '%s' "$key" | tr -d '[:space:]')"
    # skip if already set to a non-empty value
    [ -n "${!key:-}" ] && continue

    value="${line#*=}"
    # Strip surrounding quotes the way python-dotenv and Docker Compose do.
    # Without this, OPENAI_API_KEY="sk-..." reaches the app as "sk-..." (quotes
    # included) and every provider rejects it as malformed.
    case "$value" in
      \"*\") value="${value#\"}"; value="${value%\"}" ;;
      \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    # Trailing CR, in case the file was saved with Windows line endings.
    value="${value%$'\r'}"

    export "$key=$value"
  done <"$ENV_FILE"
}
load_env

# name|port|kind|path
SERVICES=(
  "auth|8081|java|backend/auth-service"
  "user|8082|java|backend/user-service"
  "document|8083|java|backend/document-service"
  "chat|8084|java|backend/chat-service"
  "gateway|8080|java|backend/gateway"
  "rag|8085|python|rag-service"
  "frontend|4200|node|frontend"
)

# ── output helpers ────────────────────────────────────────────────────────────
if [ -t 1 ]; then
  R=$'\e[31m'; G=$'\e[32m'; Y=$'\e[33m'; B=$'\e[34m'; DIM=$'\e[2m'; N=$'\e[0m'
else
  R=; G=; Y=; B=; DIM=; N=
fi
info() { printf '%s==>%s %s\n' "$B" "$N" "$*"; }
ok()   { printf '%s  ok%s %s\n' "$G" "$N" "$*"; }
warn() { printf '%swarn%s %s\n' "$Y" "$N" "$*" >&2; }
err()  { printf '%s fail%s %s\n' "$R" "$N" "$*" >&2; }
die()  { err "$*"; exit 1; }

field() { echo "$1" | cut -d'|' -f"$2"; }

find_service() {
  local want="$1" s
  for s in "${SERVICES[@]}"; do
    [ "$(field "$s" 1)" = "$want" ] && { echo "$s"; return 0; }
  done
  return 1
}

# ── docker may or may not need sudo ───────────────────────────────────────────
DOCKER=""
detect_docker() {
  [ -n "$DOCKER" ] && return 0
  if docker info >/dev/null 2>&1; then
    DOCKER="docker"
  elif sudo -n docker info >/dev/null 2>&1; then
    DOCKER="sudo docker"
  else
    return 1
  fi
}

compose() {
  detect_docker || die "cannot talk to the Docker daemon.
  Your user is not in the 'docker' group and sudo needs a password. Either:
    sudo usermod -aG docker \$USER   # then restart your WSL session (once)
  or run the database commands yourself with sudo:
    sudo docker compose -f '$ROOT/compose.yml' up -d"
  $DOCKER compose -f "$ROOT/compose.yml" "$@"
}

# ── prerequisites ─────────────────────────────────────────────────────────────
doctor() {
  local fatal=0

  info "Toolchain"
  if command -v javac >/dev/null 2>&1; then
    ok "javac $(javac -version 2>&1 | awk '{print $2}')"
  else
    err "javac NOT FOUND — you have a JRE, not a JDK. The backend cannot compile."
    printf '     fix: %ssudo apt update && sudo apt install -y openjdk-21-jdk-headless%s\n' "$DIM" "$N"
    fatal=1
  fi
  command -v java >/dev/null 2>&1 && ok "java  $(java -version 2>&1 | head -1 | cut -d'"' -f2)" \
    || { err "java not found"; fatal=1; }
  command -v node >/dev/null 2>&1 && ok "node  $(node -v)" || { err "node not found"; fatal=1; }
  command -v npm  >/dev/null 2>&1 && ok "npm   $(npm -v)"  || { err "npm not found";  fatal=1; }
  command -v python3 >/dev/null 2>&1 && ok "python $(python3 --version | awk '{print $2}')" \
    || { err "python3 not found"; fatal=1; }

  info "Docker"
  if detect_docker; then
    ok "$DOCKER ($($DOCKER --version | awk '{print $3}' | tr -d ,))"
    [ "$DOCKER" = "sudo docker" ] && warn "using sudo for docker; 'sudo usermod -aG docker \$USER' avoids this"
  else
    err "Docker daemon unreachable (not in 'docker' group, and sudo wants a password)"
    printf '     fix: %ssudo usermod -aG docker $USER%s  then restart the WSL session\n' "$DIM" "$N"
    fatal=1
  fi

  info "Project state"
  [ -f "$ROOT/backend/.mvn/wrapper/maven-wrapper.properties" ] \
    && ok "maven wrapper present" || { err "maven wrapper missing"; fatal=1; }
  [ -d "$VENV" ] && ok "python venv" || warn "python venv missing — run ./dev.sh setup"
  [ -d "$ROOT/frontend/node_modules" ] && ok "node_modules" || warn "node_modules missing — run ./dev.sh setup"
  if [ -f "$ENV_FILE" ]; then
    ok ".env present"
    local missing=()
    for v in POSTGRES_USER POSTGRES_PASSWORD JWT_SECRET; do
      [ -n "${!v:-}" ] || missing+=("$v")
    done
    if [ "${#missing[@]}" -gt 0 ]; then
      err ".env is missing required values: ${missing[*]}"
      fatal=1
    else
      ok "database + JWT settings resolved"
    fi
    if [ -z "${OPENAI_API_KEY:-}" ] || [[ "$OPENAI_API_KEY" == your_* ]] \
       || [[ "$OPENAI_API_KEY" == replace-me* ]] || [[ "$OPENAI_API_KEY" == sk-placeholder* ]]; then
      if [ "${USE_LOCAL_FALLBACK:-false}" = "true" ]; then
        warn "OPENAI_API_KEY not set, but USE_LOCAL_FALLBACK=true — rag-service will boot, chat will fail"
      else
        err "OPENAI_API_KEY not set in .env — rag-service will refuse to start"
        printf '     fix: edit %s and set OPENAI_API_KEY=sk-...\n' "$DIM$ENV_FILE$N"
        fatal=1
      fi
    else
      ok "OPENAI_API_KEY set (${OPENAI_API_KEY:0:7}…)"
    fi

    # Endpoint: empty means api.openai.com. A custom one must carry a scheme and
    # almost always needs the /v1 suffix — a missing /v1 shows up as a 404 from
    # deep inside langchain, which is a miserable thing to debug.
    local base="${EMBEDDING_BASE_URL:-${OPENAI_BASE_URL:-}}"
    local lbase="${LLM_BASE_URL:-${OPENAI_BASE_URL:-}}"
    if [ -z "${OPENAI_BASE_URL:-}${EMBEDDING_BASE_URL:-}${LLM_BASE_URL:-}" ]; then
      ok "endpoint: api.openai.com (default)"
    else
      ok "endpoint chat:       ${lbase:-api.openai.com}"
      ok "endpoint embeddings: ${base:-api.openai.com}"
      local u no_v1=0
      for u in $(printf '%s\n%s\n' "$base" "$lbase" | sort -u); do
        [ -n "$u" ] || continue
        case "$u" in
          http://*|https://*) ;;
          *) err "base URL '$u' has no http:// or https:// scheme"; fatal=1 ;;
        esac
        case "$u" in
          */v1|*/v1/|*/openai/v1|*/openai/v1/) ;;
          *) no_v1=1 ;;
        esac
      done
      # Informational only, and printed once. Many gateways (LiteLLM among them)
      # route both /chat/completions and /v1/chat/completions, so a missing /v1
      # is not necessarily wrong — it is just the first thing to try if calls 404.
      [ "$no_v1" -eq 0 ] || printf '%s       note: no /v1 suffix — fine if the gateway routes both forms; try adding it if calls 404%s\n' "$DIM" "$N"
    fi
  else
    err ".env missing — run ./dev.sh setup"
    fatal=1
  fi

  [ "$fatal" -eq 0 ] && { echo; ok "ready — ./dev.sh build && ./dev.sh up"; } \
                     || { echo; die "fix the errors above first"; }
}

# ── one-time setup ────────────────────────────────────────────────────────────
setup() {
  mkdir -p "$LOG_DIR" "$PID_DIR" "$ROOT/uploads/documents" "$ROOT/uploads/tickets"

  info "rag-service: python venv + dependencies"
  [ -d "$VENV" ] || python3 -m venv "$VENV"
  "$VENV/bin/pip" install --quiet --upgrade pip
  "$VENV/bin/pip" install --quiet -r "$ROOT/rag-service/requirements.txt"
  ok "python dependencies installed"

  if [ ! -f "$ENV_FILE" ]; then
    cp "$ROOT/.env.example" "$ENV_FILE"
    # A committed secret is a public secret — always generate a fresh one.
    if command -v openssl >/dev/null 2>&1; then
      local secret; secret="$(openssl rand -base64 48)"
      python3 - "$ENV_FILE" "$secret" <<'PY'
import sys
p, secret = sys.argv[1], sys.argv[2]
lines = open(p, encoding='utf-8').read().splitlines(keepends=True)
out = ["JWT_SECRET=%s\n" % secret if l.startswith("JWT_SECRET=") else l for l in lines]
open(p, 'w', encoding='utf-8').write("".join(out))
PY
      ok "created .env with a freshly generated JWT_SECRET"
    else
      warn "created .env — set JWT_SECRET yourself (openssl not available)"
    fi
    warn "now edit .env and set OPENAI_API_KEY"
    load_env
  else
    ok ".env already exists (left untouched)"
  fi

  # rag-service/.env is the old location; the root .env supersedes it. Leaving a
  # placeholder copy behind would silently shadow nothing (root wins) but is
  # confusing, so point it out rather than deleting the user's file.
  if [ -f "$ROOT/rag-service/.env" ]; then
    warn "rag-service/.env exists but is superseded by the root .env — safe to delete"
  fi

  info "frontend: npm install"
  (cd "$ROOT/frontend" && npm install --no-fund --no-audit)
  ok "node dependencies installed"

  echo; ok "setup complete — ./dev.sh build && ./dev.sh up"
}

build() {
  command -v javac >/dev/null 2>&1 || die "javac not found — see ./dev.sh doctor"
  info "Building 5 Spring services (aggregator)"
  "$ROOT/backend/mvnw" -f "$ROOT/backend" install -DskipTests
  ok "backend built"
}

# ── process control ───────────────────────────────────────────────────────────
pid_of() {
  local f="$PID_DIR/$1.pid"
  [ -f "$f" ] || return 1
  local p; p="$(cat "$f")"
  kill -0 "$p" 2>/dev/null && { echo "$p"; return 0; }
  rm -f "$f"; return 1
}

port_busy() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null && { exec 3>&-; return 0; } || return 1; }

start_one() {
  local spec name port kind path
  spec="$1"; name=$(field "$spec" 1); port=$(field "$spec" 2)
  kind=$(field "$spec" 3); path=$(field "$spec" 4)

  if pid_of "$name" >/dev/null; then ok "$name already running (pid $(pid_of "$name"))"; return 0; fi
  if port_busy "$port"; then warn "$name: port $port already in use by something else — skipping"; return 0; fi

  mkdir -p "$LOG_DIR"
  local log="$LOG_DIR/$name.log"

  case "$kind" in
    java)
      # Absolute upload paths so the location does not depend on CWD.
      "$ROOT/backend/mvnw" -f "$ROOT/$path" spring-boot:run \
        -Dspring-boot.run.jvmArguments="-Dapp.upload.dir=$ROOT/uploads -Ddocument.upload.path=$ROOT/uploads/documents" \
        >"$log" 2>&1 &
      ;;
    python)
      [ -x "$VENV/bin/uvicorn" ] || die "uvicorn missing — run ./dev.sh setup"
      (cd "$ROOT/rag-service" && exec "$VENV/bin/uvicorn" app.main:app \
        --reload --host 0.0.0.0 --port "$port") >"$log" 2>&1 &
      ;;
    node)
      [ -d "$ROOT/frontend/node_modules" ] || die "node_modules missing — run ./dev.sh setup"
      (cd "$ROOT/frontend" && exec npm start -- --port "$port") >"$log" 2>&1 &
      ;;
  esac

  echo $! >"$PID_DIR/$name.pid"
  ok "$name starting on :$port  ${DIM}(log: .dev/logs/$name.log)${N}"
}

wait_http() {
  local name="$1" url="$2" tries="${3:-90}" i
  for ((i = 0; i < tries; i++)); do
    if curl -fsS -o /dev/null --max-time 2 "$url" 2>/dev/null; then ok "$name responding"; return 0; fi
    pid_of "$name" >/dev/null || { err "$name died — tail .dev/logs/$name.log"; return 1; }
    sleep 2
  done
  warn "$name not responding yet after $((tries * 2))s (may still be warming up)"
  return 0
}

up() {
  mkdir -p "$LOG_DIR" "$PID_DIR" "$ROOT/uploads/documents" "$ROOT/uploads/tickets"

  info "Starting databases"
  compose up -d
  info "Waiting for databases to report healthy"
  local i healthy=0
  for ((i = 0; i < 60; i++)); do
    if [ "$(compose ps --format '{{.Health}}' 2>/dev/null | grep -c healthy || true)" -ge 2 ]; then
      healthy=1; break
    fi
    sleep 2
  done
  [ "$healthy" -eq 1 ] && ok "both databases healthy" || warn "databases not healthy yet; continuing anyway"

  local targets=()
  if [ "$#" -gt 0 ]; then
    local a spec
    for a in "$@"; do
      spec="$(find_service "$a")" || die "unknown service '$a' (valid: auth user document chat gateway rag frontend)"
      targets+=("$spec")
    done
  else
    targets=("${SERVICES[@]}")
  fi

  info "Starting application processes"
  local spec
  for spec in "${targets[@]}"; do start_one "$spec"; done

  echo
  info "Health checks (Spring services take ~20-40s each on a cold start)"
  for spec in "${targets[@]}"; do
    local name port; name=$(field "$spec" 1); port=$(field "$spec" 2)
    case "$name" in
      rag)      wait_http rag      "http://localhost:$port/health" || true ;;
      frontend) wait_http frontend "http://localhost:$port/"       || true ;;
      *)        wait_http "$name"  "http://localhost:$port/v3/api-docs" || true ;;
    esac
  done

  echo; status
}

down() {
  local stop_db=0
  [ "${1:-}" = "--db" ] && stop_db=1

  info "Stopping application processes"
  local spec name p
  for spec in "${SERVICES[@]}"; do
    name=$(field "$spec" 1)
    if p="$(pid_of "$name")"; then
      # negative pid => whole process group, so mvn's forked JVM dies too
      kill -TERM "-$p" 2>/dev/null || kill -TERM "$p" 2>/dev/null || true
      ok "$name stopped (pid $p)"
      rm -f "$PID_DIR/$name.pid"
    fi
  done

  # Spring Boot forks a JVM that can outlive the mvn wrapper.
  pkill -f 'spring-boot:run' 2>/dev/null || true
  pkill -f 'com.alten.*Application' 2>/dev/null || true

  if [ "$stop_db" -eq 1 ]; then
    info "Stopping databases"; compose down; ok "databases stopped (data preserved)"
  else
    printf '%s     databases left running — ./dev.sh down --db to stop them%s\n' "$DIM" "$N"
  fi
}

status() {
  info "Databases"
  if detect_docker; then
    compose ps --format '  {{.Name}}  {{.State}}  {{.Health}}  {{.Publishers}}' 2>/dev/null \
      || echo "  (compose not up)"
  else
    echo "  (docker unreachable)"
  fi

  info "Application processes"
  printf '  %-10s %-6s %-9s %s\n' SERVICE PORT PID STATE
  local spec name port p state
  for spec in "${SERVICES[@]}"; do
    name=$(field "$spec" 1); port=$(field "$spec" 2)
    if p="$(pid_of "$name")"; then
      port_busy "$port" && state="${G}listening${N}" || state="${Y}starting${N}"
    else
      p="-"
      port_busy "$port" && state="${Y}port used by other proc${N}" || state="${DIM}stopped${N}"
    fi
    printf '  %-10s %-6s %-9s %b\n' "$name" "$port" "$p" "$state"
  done

  cat <<EOF

  Frontend    http://localhost:4200
  Gateway     http://localhost:8080        (note: the Angular app calls 8081-8084 directly)
  Swagger     http://localhost:8081/swagger-ui/index.html   (also 8082/8083/8084)
  RAG health  http://localhost:8085/health
  RAG docs    http://localhost:8085/docs
EOF
}

logs() {
  mkdir -p "$LOG_DIR"
  if [ "$#" -eq 0 ]; then
    local f=()
    for spec in "${SERVICES[@]}"; do
      local n; n=$(field "$spec" 1)
      [ -f "$LOG_DIR/$n.log" ] && f+=("$LOG_DIR/$n.log")
    done
    [ "${#f[@]}" -gt 0 ] || die "no logs yet — ./dev.sh up first"
    tail -n 40 -F "${f[@]}"
  else
    find_service "$1" >/dev/null || die "unknown service '$1'"
    local lf="$LOG_DIR/$1.log"
    [ -f "$lf" ] || die "no log for '$1' yet"
    tail -n 200 -F "$lf"
  fi
}

restart() {
  [ "$#" -gt 0 ] || die "usage: ./dev.sh restart <service>"
  local a spec name p
  for a in "$@"; do
    spec="$(find_service "$a")" || die "unknown service '$a'"
    name=$(field "$spec" 1)
    if p="$(pid_of "$name")"; then
      kill -TERM "-$p" 2>/dev/null || kill -TERM "$p" 2>/dev/null || true
      rm -f "$PID_DIR/$name.pid"; sleep 2
    fi
    start_one "$spec"
  done
}

usage() { sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; }

cmd="${1:-}"; shift || true
case "$cmd" in
  doctor)  doctor ;;
  setup)   setup ;;
  build)   build ;;
  up)      up "$@" ;;
  down)    down "$@" ;;
  status)  status ;;
  logs)    logs "$@" ;;
  restart) restart "$@" ;;
  ""|-h|--help|help) usage ;;
  *)       err "unknown command '$cmd'"; echo; usage; exit 1 ;;
esac
