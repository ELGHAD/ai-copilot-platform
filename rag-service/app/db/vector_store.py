import psycopg
from app.config import get_settings
import logging

logger = logging.getLogger(__name__)

settings = get_settings()


def get_raw_connection_string() -> str:
    """
    Convertit l'URL SQLAlchemy en URL psycopg3 pure.
    SQLAlchemy : postgresql+psycopg://...
    psycopg3   : postgresql://...
    """
    return settings.database_url.replace("postgresql+psycopg://", "postgresql://")


def init_database() -> bool:
    """
    Initialise la base de données :
    1. Crée l'extension pgvector si elle n'existe pas
    2. Vérifie la connexion
    Appelé au démarrage du service.
    """
    try:
        conn_string = get_raw_connection_string()

        with psycopg.connect(conn_string) as conn:
            with conn.cursor() as cur:

                # Activer l'extension pgvector
                cur.execute("CREATE EXTENSION IF NOT EXISTS vector;")
                conn.commit()

                # Vérifier que pgvector est bien actif
                cur.execute(
                    "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
                )
                result = cur.fetchone()

                if result:
                    logger.info(
                        f"pgvector activé — version {result[1]}"
                    )
                else:
                    logger.error("pgvector non disponible dans cette base")
                    return False

        logger.info("Base de données initialisée avec succès")
        return True

    except Exception as e:
        logger.error(f"Erreur initialisation base de données : {e}")
        raise


def check_connection() -> bool:
    """
    Vérifie que la connexion à la base est active.
    Utilisé par le health check du service.
    """
    try:
        conn_string = get_raw_connection_string()

        with psycopg.connect(conn_string) as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1;")
                return True

    except Exception as e:
        logger.error(f"Connexion base de données échouée : {e}")
        return False