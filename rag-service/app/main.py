from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from app.routers import documents, chat
from app.db.vector_store import init_database, check_connection
from app.models import HealthResponse
from app.config import get_settings
import logging
import sys

# Configuration du logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s — %(name)s — %(levelname)s — %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)

logger = logging.getLogger(__name__)
settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Cycle de vie du service :
    - Démarrage : initialiser la base de données si elle est disponible
    - Arrêt : nettoyage
    """
    logger.info("Démarrage du RAG Service...")

    # Vérification de la clé OpenAI AVANT d'accepter du trafic.
    # Sans ce contrôle, le service démarrait normalement et n'échouait qu'au
    # premier appel /chat/, ce qui donnait une erreur 500 opaque très loin de
    # la cause réelle.
    if not settings.has_openai_credentials:
        message = settings.credentials_error()
        if settings.use_local_fallback:
            logger.warning(
                "USE_LOCAL_FALLBACK=true — démarrage sans clé OpenAI. "
                "Les endpoints /chat/ et l'indexation échoueront.\n%s", message
            )
        else:
            logger.error("Configuration invalide :\n%s", message)
            raise RuntimeError(message)

    try:
        init_database()
        logger.info("RAG Service prêt ✓")
    except Exception as e:
        logger.warning(
            "Base de données indisponible au démarrage : %s. "
            "Le service continue en mode dégradé.", e
        )

    yield

    logger.info("Arrêt du RAG Service...")


app = FastAPI(
    title="ALTEN AI Copilot — RAG Service",
    description="Service de Retrieval-Augmented Generation pour les documents internes ALTEN",
    version="1.0.0",
    lifespan=lifespan
)

# Configuration CORS — autorise le gateway et le frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:4200",   # Angular frontend
        "http://localhost:8080",   # API Gateway
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Enregistrement des routers
app.include_router(documents.router)
app.include_router(chat.router)


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """
    Health check global du service.
    Vérifie la connexion à la base de données.
    """
    db_ok = check_connection()

    if not db_ok:
        return HealthResponse(
            status="degraded",
            service="rag-service",
            version="1.0.0"
        )

    return HealthResponse(
        status="ok",
        service="rag-service",
        version="1.0.0"
    )


@app.get("/")
async def root():
    return {
        "service": "ALTEN AI Copilot — RAG Service",
        "version": "1.0.0",
        "docs": "/docs",
        "health": "/health"
    }