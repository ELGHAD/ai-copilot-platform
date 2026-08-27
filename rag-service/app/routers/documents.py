from fastapi import APIRouter, UploadFile, File, HTTPException, Depends, Form
from app.models import DocumentUploadResponse, DocumentInfo, DeleteResponse
from app.services.parser import DocumentParser
from app.services.chunker import DocumentChunker
from app.services.embedder import VectorStoreManager
import tempfile
import os
import uuid
from datetime import datetime
import logging

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/documents", tags=["Documents"])

# Instances des services
parser = DocumentParser()
chunker = DocumentChunker()


def get_vector_store() -> VectorStoreManager:
    return VectorStoreManager()


@router.post("/upload", response_model=DocumentUploadResponse)
async def upload_document(
    file: UploadFile = File(...),
    role_access: str = Form(default="COMMUN"),
    vector_store: VectorStoreManager = Depends(get_vector_store)
):
    """
    Upload et indexe un document PDF ou DOCX.
    Pipeline complet : upload → parse → chunk → embed → store
    """
    # Validation du type de fichier
    allowed_types = [
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ]
    allowed_extensions = [".pdf", ".docx"]
    file_ext = os.path.splitext(file.filename)[1].lower()

    if file_ext not in allowed_extensions:
        raise HTTPException(
            status_code=400,
            detail=f"Format non supporté : {file_ext}. Utilisez PDF ou DOCX."
        )

    # Validation de la taille (max 50MB)
    MAX_SIZE = 50 * 1024 * 1024
    content = await file.read()

    if len(content) > MAX_SIZE:
        raise HTTPException(
            status_code=400,
            detail="Fichier trop volumineux. Maximum 50MB."
        )

    normalized_role = (role_access or "COMMUN").upper()
    document_id = str(uuid.uuid4())

    # Sauvegarder temporairement le fichier
    with tempfile.NamedTemporaryFile(
        delete=False,
        suffix=file_ext
    ) as tmp_file:
        tmp_file.write(content)
        tmp_path = tmp_file.name

    try:
        logger.info(
            f"Upload reçu — fichier: {file.filename} — "
            f"taille: {len(content)} bytes — "
            f"role_access: {normalized_role} — "
            f"document_id: {document_id}"
        )

        # Étape 1 : Parser le document
        parsed_pages = parser.parse(tmp_path, file.filename)

        if not parsed_pages:
            raise HTTPException(
                status_code=422,
                detail="Le document est vide ou illisible."
            )

        # Étape 2 : Chunker
        chunks = chunker.chunk(parsed_pages, document_id, role_access=normalized_role)

        if not chunks:
            raise HTTPException(
                status_code=422,
                detail="Impossible d'extraire du contenu de ce document."
            )

        # Log des stats de chunking
        stats = chunker.get_stats(chunks)
        logger.info(f"Stats chunking : {stats}")

        # Étape 3 : Embed et stocker dans pgvector
        stored_count = vector_store.add_chunks(chunks, document_id)

        return DocumentUploadResponse(
            document_id=document_id,
            filename=file.filename,
            chunks_count=stored_count,
            message=f"Document indexé avec succès — {stored_count} chunks créés."
        )

    except HTTPException:
        raise

    except Exception as e:
        logger.error(f"Erreur upload document {file.filename} : {e}")
        raise HTTPException(
            status_code=500,
            detail=f"Erreur lors du traitement du document : {str(e)}"
        )

    finally:
        # Toujours supprimer le fichier temporaire
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


@router.delete("/{document_id}", response_model=DeleteResponse)
async def delete_document(
    document_id: str,
    vector_store: VectorStoreManager = Depends(get_vector_store)
):
    """
    Supprime un document et tous ses chunks du vector store.
    """
    try:
        vector_store.delete_document(document_id)

        return DeleteResponse(
            document_id=document_id,
            message="Document supprimé avec succès."
        )

    except Exception as e:
        logger.error(f"Erreur suppression document {document_id} : {e}")
        raise HTTPException(
            status_code=500,
            detail=f"Erreur lors de la suppression : {str(e)}"
        )