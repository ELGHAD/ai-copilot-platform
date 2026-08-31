from fastapi import APIRouter, HTTPException, Depends
from app.models import ChatRequest, ChatResponse, SourceDocument
from app.services.embedder import VectorStoreManager
from app.services.rag_chain import RAGChain
import uuid
import logging

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/chat", tags=["Chat"])


def get_rag_chain() -> RAGChain:
    vector_store = VectorStoreManager()
    return RAGChain(vector_store)


# Les deux formes sont servies directement. Sans la variante sans slash, un POST
# sur /chat renvoie une redirection 307 que la plupart des clients HTTP ne suivent
# pas pour un POST — l'appelant reçoit alors un corps vide au lieu d'une réponse.
@router.post("/", response_model=ChatResponse)
@router.post("", response_model=ChatResponse, include_in_schema=False)
async def chat(
    request: ChatRequest,
    rag_chain: RAGChain = Depends(get_rag_chain)
):
    """
    Endpoint principal du RAG.
    Reçoit une question, retourne une réponse basée sur les documents indexés.
    """
    if not request.question.strip():
        raise HTTPException(
            status_code=400,
            detail="La question ne peut pas être vide."
        )

    if len(request.question) > 2000:
        raise HTTPException(
            status_code=400,
            detail="Question trop longue. Maximum 2000 caractères."
        )

    # Générer un session_id si non fourni
    session_id = request.session_id or str(uuid.uuid4())
    requested_role = (request.role or "OPERATIONNEL").upper().replace("ROLE_", "")

    try:
        logger.info(
            f"Question reçue — session: {session_id} — "
            f"role: {requested_role} — "
            f"question: {request.question[:100]}..."
        )

        # Invoquer la chaîne RAG
        result = rag_chain.invoke(request.question, requested_role)

        # Construire les sources
        sources = [
            SourceDocument(
                content=src["content"],
                source=src["source"],
                page=src.get("page")
            )
            for src in result["sources"]
        ]

        return ChatResponse(
            answer=result["answer"],
            sources=sources,
            session_id=session_id
        )

    except Exception as e:
        logger.error(f"Erreur chat session {session_id} : {e}")
        raise HTTPException(
            status_code=500,
            detail=f"Erreur lors du traitement de la question : {str(e)}"
        )


@router.get("/health")
async def chat_health():
    """
    Health check rapide du router chat.
    """
    return {"status": "ok", "router": "chat"}