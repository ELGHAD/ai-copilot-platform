from pydantic_settings import BaseSettings
from dotenv import load_dotenv
import os

# Forcer le chargement du .env avec override
load_dotenv(os.path.join(os.path.dirname(os.path.dirname(__file__)), ".env"), override=True)


class Settings(BaseSettings):
    openai_api_key: str = ""
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
        env_file = ".env"
        env_file_encoding = "utf-8"

    @property
    def has_openai_credentials(self) -> bool:
        return bool(
            self.openai_api_key
            and self.openai_api_key.strip()
            and not self.openai_api_key.startswith("sk-placeholder")
            and not self.openai_api_key.lower().startswith("your_")
        )


def get_settings() -> Settings:
    return Settings()