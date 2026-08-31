# ALTEN AI Copilot

Document-grounded RAG chat platform: Angular frontend, five Spring Boot
microservices, and a FastAPI RAG service over PostgreSQL + pgvector.

## Quick start

```bash
./dev.sh doctor     # check prerequisites (changes nothing)
./dev.sh setup      # one-time: python venv, npm install, .env scaffold
./dev.sh build      # compile the 5 Spring services
./dev.sh up         # databases in Docker + all 7 app processes
```

Then open <http://localhost:4200>.

`./dev.sh` with no arguments lists every command. The useful ones:

| Command | Effect |
|---|---|
| `./dev.sh up` | Databases + all app processes, with health checks |
| `./dev.sh up chat rag` | Databases + only the named services |
| `./dev.sh status` | What is running, what is listening, all the URLs |
| `./dev.sh logs` | Tail every log at once |
| `./dev.sh logs chat` | Tail one service |
| `./dev.sh restart chat` | Restart one service |
| `./dev.sh down` | Stop app processes, leave databases up |
| `./dev.sh down --db` | Stop app processes and databases |

Logs land in `.dev/logs/<service>.log`, PIDs in `.dev/pids/`.

## Prerequisites

- **JDK 21** — a JRE is not enough, the backend needs `javac`:
  `sudo apt install -y openjdk-21-jdk`
- **Docker**, usable without sudo: `sudo usermod -aG docker $USER`, then restart
  your WSL session
- Node 20+, Python 3.11+

`./dev.sh doctor` verifies all of these and prints the fix for anything missing.

## Configuration

All configuration lives in a single gitignored **`.env` at the repo root**,
created by `./dev.sh setup` from `.env.example`. `dev.sh` exports it to every
process it launches, and Docker Compose reads it directly, so the five Spring
services and the rag-service all resolve the same values — importantly the same
`JWT_SECRET`, since they all validate the same tokens.

Nothing secret remains in `application.properties`; those now reference
`${POSTGRES_PASSWORD}`, `${JWT_SECRET}` and friends.

You must set one value by hand:

```bash
OPENAI_API_KEY=sk-...
```

rag-service **refuses to start** without it and prints exactly what to fix.
Previously a missing key let the service boot and only failed on the first
`/chat/` call, as an opaque 500. To work on non-AI parts without a key, set
`USE_LOCAL_FALLBACK=true` — the service boots and serves `/health`, and chat
still fails.

### Using a gateway instead of api.openai.com

Leave `OPENAI_BASE_URL` empty to use OpenAI directly. Set it to target any
OpenAI-compatible endpoint (LiteLLM, OpenRouter, vLLM, Ollama, a corporate
gateway):

```bash
OPENAI_BASE_URL=https://your-gateway.example.com     # add /v1 if it needs it
EMBEDDING_BASE_URL=                                  # optional split
LLM_BASE_URL=                                        # optional split
```

The split overrides exist because many gateways serve chat but not embeddings.
Empty values fall back to `OPENAI_BASE_URL`.

Two gotchas this handles for you:

- **Token arrays.** langchain pre-tokenises text and POSTs integer arrays to
  `/embeddings`. OpenAI accepts that; most gateways reject it and want raw
  strings. `EMBEDDING_CHECK_CTX_LENGTH` controls this and defaults to
  automatic — raw text whenever a custom base URL is set. Only set it
  explicitly if the automatic choice is wrong for your gateway.
- **Quoted values.** `KEY="sk-..."` in `.env` works; the quotes are stripped
  the same way `python-dotenv` and Compose strip them.

**Model names are not portable.** A gateway will not necessarily serve the
OpenAI defaults — `gpt-4o-mini` in particular is often absent. Check first:

```bash
curl -H "Authorization: Bearer $OPENAI_API_KEY" "$OPENAI_BASE_URL/v1/models"
```

`EMBEDDING_MODEL` must produce **1536-dimension** vectors to match the existing
pgvector collection (`text-embedding-3-small` and `ada-002` both do). Switching
to a different dimension means re-indexing every document.

Azure OpenAI is **not** plain-compatible — it needs `AzureChatOpenAI`, an
api-version and deployment names. `base_url` alone will not work there.

`./dev.sh setup` generates a fresh `JWT_SECRET` for you. The secret that used to
be hardcoded in all five properties files is committed in git history and must
be treated as public — do not put it back.

Running a service manually, outside `dev.sh`, needs the environment loaded first:

```bash
set -a; . ./.env; set +a
cd backend/auth-service && ./mvnw spring-boot:run
```

## Layout

| Path | Role | Port |
|---|---|---|
| `frontend/` | Angular 21 + Material + Chart.js | 4200 |
| `backend/gateway` | Spring Cloud Gateway | 8080 |
| `backend/auth-service` | Login, register, JWT issuing | 8081 |
| `backend/user-service` | Users, roles, profile | 8082 |
| `backend/document-service` | Document metadata + upload | 8083 |
| `backend/chat-service` | Conversations, tickets, attachments | 8084 |
| `rag-service/` | FastAPI + LangChain, parse/chunk/embed/retrieve | 8085 |

Databases (`compose.yml`):

| Database | Port | Used by |
|---|---|---|
| `alten_copilot` | 5433 | the four business Spring services |
| `rag_db` (pgvector) | 5435 | rag-service |

Schemas are created automatically (`spring.jpa.hibernate.ddl-auto=update`, and
LangChain creates its own vector tables).

Roles: `ADMIN`, `EXPERT`, `OPERATIONNEL`.

## Backend builds

`backend/pom.xml` is a pure aggregator, so both of these work:

```bash
./backend/mvnw -f backend install -DskipTests          # all five
./backend/mvnw -f backend -pl auth-service test        # one module
cd backend/auth-service && ./mvnw spring-boot:run      # unchanged
```

## How requests flow

The browser only ever talks to `localhost:4200`. The Angular dev-server proxies
`/api` and `/uploads` to the gateway, which routes on to the individual services:

```
browser → :4200 (ng serve, proxy.conf.json)
            → :8080 gateway  ─ /api/auth/**      → :8081 auth-service
                             ─ /api/users/**     → :8082 user-service
                             ─ /api/documents/** → :8083 document-service
                             ─ /api/chat/**      → :8084 chat-service
                             ─ /api/tickets/**   → :8084 chat-service
                             ─ /uploads/**       → :8084 chat-service (no JWT)
                                :8083/:8084 → :8085 rag-service
```

Because everything is same-origin through the proxy, CORS is not involved in
development at all. Services build relative URLs from `environment.apiBaseUrl`
(empty in dev); set it to an absolute origin to bypass the proxy.

## Things to be aware of

- **Upload paths are CWD-relative** in the properties files
  (`app.upload.dir=uploads`, `document.upload.path=uploads/documents`), so
  launching a service from its own folder puts uploads somewhere different than
  launching from the repo root. `dev.sh` pins both to `<repo>/uploads` via `-D`
  so it no longer matters — but a manual `cd backend/chat-service && ./mvnw
  spring-boot:run` still has the original behaviour.
- `rag-service/docker-compose.yml` is superseded by the root `compose.yml`. Do
  not run both; they both bind port 5435.
- `rag-service/.env` is superseded by the root `.env`. If you have one, the root
  file wins; delete the old one to avoid confusion.
- **`ng build` (production) currently fails** on a pre-existing CSS budget
  violation: `chat.scss` is 16 kB against an 8 kB `anyComponentStyle` budget in
  `angular.json`. This predates the tooling work here and is untouched by it —
  `ng build --configuration development` and `ng serve` are unaffected. Fix by
  trimming that stylesheet or raising the budget.
