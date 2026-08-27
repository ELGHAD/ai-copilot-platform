from langchain_openai import OpenAIEmbeddings
from langchain_postgres.vectorstores import PGVector
from langchain_core.embeddings import FakeEmbeddings
from langchain.schema import Document
from app.config import get_settings
import logging

logger = logging.getLogger(__name__)

settings = get_settings()


class VectorStoreManager:

    def __init__(self):
        """
        Initialise les embeddings OpenAI ou, si aucune clé n'est disponible,
        un fallback local pour que le service reste utilisable.
        """
        if settings.has_openai_credentials and not settings.use_local_fallback:
            self.embeddings = OpenAIEmbeddings(
                model=settings.embedding_model,
                api_key=settings.openai_api_key
            )
            logger.info("VectorStore initialisé avec OpenAI embeddings")
        else:
            self.embeddings = FakeEmbeddings(size=1536)
            logger.warning(
                "Utilisation du fallback local pour les embeddings. "
                "Le service reste fonctionnel sans accès externe OpenAI."
            )

        self.collection_name = "alten_documents"

        self.vector_store = PGVector(
            embeddings=self.embeddings,
            collection_name=self.collection_name,
            connection=settings.database_url,
            use_jsonb=True
        )

        logger.info(
            f"VectorStore initialisé — "
            f"collection: {self.collection_name} — "
            f"modèle: {settings.embedding_model}"
        )

    def add_chunks(self, chunks: list[Document], document_id: str) -> int:
        """
        Embed et stocke les chunks dans pgvector.
        Retourne le nombre de chunks stockés.
        """
        if not chunks:
            logger.warning(f"Aucun chunk à stocker pour document_id={document_id}")
            return 0

        try:
            self.vector_store.add_documents(chunks)
            logger.info(
                f"Document {document_id} — "
                f"{len(chunks)} chunks embedés et stockés dans pgvector"
            )
            return len(chunks)

        except Exception as e:
            logger.error(f"Erreur stockage chunks document {document_id} : {e}")
            raise

    def delete_document(self, document_id: str) -> bool:
        """
        Supprime tous les chunks d'un document du vector store.
        """
        try:
            self.vector_store.delete(
                filter={"document_id": document_id}
            )
            logger.info(f"Document {document_id} supprimé du vector store")
            return True

        except Exception as e:
            logger.error(f"Erreur suppression document {document_id} : {e}")
            raise

    def get_retriever(self):
        """
        Retourne un retriever configuré pour récupérer les chunks
        les plus pertinents via similarité cosinus.

        search_type='mmr' : Maximum Marginal Relevance
        → Évite les chunks redondants dans les résultats
        → Meilleure diversité = meilleure réponse finale
        """
        return self.vector_store.as_retriever(
            search_type="mmr",
            search_kwargs={
                "k": settings.max_retrieved_docs,
                "fetch_k": settings.max_retrieved_docs * 3,
                "lambda_mult": 0.7
            }
        )

    def similarity_search(self, query: str, k: int = None) -> list[Document]:
        """
        Recherche directe par similarité — utilisée pour le debugging.
        """
        k = k or settings.max_retrieved_docs
        return self.vector_store.similarity_search(query, k=k)