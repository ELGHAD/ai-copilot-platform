from pydantic_settings import BaseSettings
from dotenv import load_dotenv
from pathlib import Path
import os

# ─── Chargement de la configuration ──────────────────────────────────────────
# Ordre de priorité (le premier trouvé gagne, override=False) :
#   1. les vraies variables d'environnement  (ce que ./dev.sh exporte)
#   2. le .env à la racine du dépôt          (source de vérité unique)
#   3. rag-service/.env                      (ancien emplacement, compatibilité)
#
# override=False est important : sinon un fichier .env écraserait les variables
# déjà exportées par dev.sh, et le service ne verrait pas la même valeur de clé
# que les autres services.
_APP_DIR = Path(__file__).resolve().parent
_SERVICE_DIR = _APP_DIR.parent
_REPO_ROOT = _SERVICE_DIR.parent

ROOT_ENV_FILE = _REPO_ROOT / ".env"
LEGACY_ENV_FILE = _SERVICE_DIR / ".env"

load_dotenv(ROOT_ENV_FILE, override=False)
load_dotenv(LEGACY_ENV_FILE, override=False)


class Settings(BaseSettings):
    openai_api_key: str = ""

    # ── Endpoint ─────────────────────────────────────────────────────────────
    # Vide  → api.openai.com (comportement par défaut du SDK).
    # Rempli → n'importe quelle API compatible OpenAI (LiteLLM, OpenRouter,
    #          vLLM, Ollama, passerelle interne...). Doit inclure le suffixe
    #          attendu par la passerelle, en général « /v1 ».
    openai_base_url: str = ""

    # Surcharges optionnelles : beaucoup de passerelles servent le chat mais pas
    # les embeddings (ou l'inverse). Vides → openai_base_url est utilisé.
    embedding_base_url: str = ""
    llm_base_url: str = ""

    # langchain-openai pré-tokenise les textes et envoie des tableaux d'entiers
    # à /embeddings. C'est valide pour OpenAI, mais la majorité des passerelles
    # compatibles rejettent ce format et exigent du texte brut.
    # None → auto : désactivé dès qu'une base URL personnalisée est configurée.
    embedding_check_ctx_length: bool | None = None

    database_url: str = "postgresql+psycopg://postgres:hamza@localhost:5435/rag_db"
    chunk_size: int = 800
    chunk_overlap: int = 150
    max_retrieved_docs: int = 6
    embedding_model: str = "text-embedding-3-small"
    llm_model: str = "gpt-4o-mini"
    llm_temperature: float = 0.0
    rag_service_host: str = "0.0.0.0"
    rag_service_port: int = 8085
    use_local_fallback: bool = False

    class Config:
        env_file = str(ROOT_ENV_FILE)
        env_file_encoding = "utf-8"
        # Le .env racine est partagé avec les services Spring et Docker Compose :
        # il contient POSTGRES_*, JWT_* etc. qui ne concernent pas ce service.
        # Sans "ignore", pydantic-settings rejette ces clés (extra_forbidden) et
        # le service ne démarre plus du tout.
        extra = "ignore"

    # ── Endpoints effectifs ──────────────────────────────────────────────────
    @property
    def effective_embedding_base_url(self) -> str:
        """URL de base pour /embeddings ('' = api.openai.com)."""
        return (self.embedding_base_url or self.openai_base_url).rstrip("/")

    @property
    def effective_llm_base_url(self) -> str:
        """URL de base pour /chat/completions ('' = api.openai.com)."""
        return (self.llm_base_url or self.openai_base_url).rstrip("/")

    @property
    def uses_custom_endpoint(self) -> bool:
        return bool(self.effective_embedding_base_url or self.effective_llm_base_url)

    @property
    def should_check_embedding_ctx_length(self) -> bool:
        """
        True  → langchain envoie des tableaux de tokens (format OpenAI natif).
        False → langchain envoie le texte brut (ce qu'attendent la plupart des
                passerelles compatibles).
        Réglage explicite prioritaire ; sinon auto d'après l'endpoint.
        """
        if self.embedding_check_ctx_length is not None:
            return self.embedding_check_ctx_length
        return not self.effective_embedding_base_url

    @property
    def has_openai_credentials(self) -> bool:
        return bool(
            self.openai_api_key
            and self.openai_api_key.strip()
            and not self.openai_api_key.startswith("sk-placeholder")
            and not self.openai_api_key.lower().startswith("your_")
            and not self.openai_api_key.lower().startswith("replace-me")
        )

    def credentials_error(self) -> str:
        """
        Message d'erreur explicite quand la clé OpenAI est absente ou factice.
        Retourne une chaîne vide si la configuration est valide.
        """
        if self.has_openai_credentials:
            return ""

        which = ROOT_ENV_FILE if ROOT_ENV_FILE.exists() else LEGACY_ENV_FILE
        detail = "absente" if not self.openai_api_key.strip() else "encore un placeholder"
        return (
            f"OPENAI_API_KEY est {detail}.\n"
            f"  Renseignez-la dans : {which}\n"
            f"      OPENAI_API_KEY=sk-...\n"
            f"  Clés disponibles sur https://platform.openai.com/api-keys\n"
            f"  Pour démarrer sans clé (endpoints AI non fonctionnels) : "
            f"USE_LOCAL_FALLBACK=true"
        )


def get_settings() -> Settings:
    return Settings()
