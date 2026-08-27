from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain.schema import Document
from app.config import get_settings
import logging

logger = logging.getLogger(__name__)

settings = get_settings()


class DocumentChunker:

    def __init__(self):
        """
        RecursiveCharacterTextSplitter est le meilleur choix pour des documents
        techniques en français. Il découpe en respectant cette hiérarchie :
        1. Paragraphes (\n\n)
        2. Lignes (\n)
        3. Phrases (. ! ?)
        4. Mots (espace)
        Jamais au milieu d'un mot ou d'une phrase si possible.
        """
        self.splitter = RecursiveCharacterTextSplitter(
            chunk_size=settings.chunk_size,          # 800 tokens par chunk
            chunk_overlap=settings.chunk_overlap,    # 150 tokens overlap entre chunks
            length_function=len,
            separators=[
                "\n\n",   # Priorité 1 : paragraphes
                "\n",     # Priorité 2 : lignes
                ". ",     # Priorité 3 : fins de phrases
                "! ",
                "? ",
                "; ",
                ", ",
                " ",      # Priorité 4 : mots
                ""        # Dernier recours : caractères
            ]
        )

    def chunk(self, parsed_pages: list[dict], document_id: str, role_access: str = "COMMUN") -> list[Document]:
        """
        Transforme les pages/sections parsées en chunks LangChain
        avec métadonnées enrichies.
        """
        all_chunks = []

        for page_data in parsed_pages:
            content = page_data["content"]
            metadata = page_data["metadata"]

            # Découper le contenu en chunks
            raw_chunks = self.splitter.split_text(content)

            for chunk_index, chunk_text in enumerate(raw_chunks):

                # Filtrer les chunks trop courts (bruit)
                if len(chunk_text.strip()) < 50:
                    continue

                # Enrichir les métadonnées pour chaque chunk
                chunk_metadata = {
                    **metadata,
                    "document_id": document_id,
                    "role_access": (role_access or "COMMUN").upper(),
                    "chunk_index": chunk_index,
                    "chunk_total": len(raw_chunks),
                    "chunk_size": len(chunk_text),
                    "source": metadata.get("source", "Document inconnu")
                }

                doc = Document(
                    page_content=chunk_text,
                    metadata=chunk_metadata
                )
                all_chunks.append(doc)

        logger.info(
            f"Document {document_id} — "
            f"{len(parsed_pages)} pages/sections → "
            f"{len(all_chunks)} chunks générés "
            f"(taille: {settings.chunk_size}, overlap: {settings.chunk_overlap})"
        )

        return all_chunks

    def get_stats(self, chunks: list[Document]) -> dict:
        """
        Retourne des statistiques sur les chunks générés.
        Utile pour le debugging et l'optimisation.
        """
        if not chunks:
            return {"total": 0}

        sizes = [len(c.page_content) for c in chunks]

        return {
            "total_chunks": len(chunks),
            "avg_size": round(sum(sizes) / len(sizes)),
            "min_size": min(sizes),
            "max_size": max(sizes),
            "total_chars": sum(sizes)
        }