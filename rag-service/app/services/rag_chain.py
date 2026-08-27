from langchain_openai import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from langchain.schema import StrOutputParser
from langchain.schema.runnable import RunnablePassthrough, RunnableParallel
from langchain.schema import Document
from app.config import get_settings
from app.services.embedder import VectorStoreManager
import logging

logger = logging.getLogger(__name__)

settings = get_settings()


SYSTEM_PROMPT = """Tu es un assistant IA interne d'ALTEN, une entreprise d'ingénierie et de conseil en technologie.

Ton rôle est d'aider les employés en répondant à leurs questions en te basant UNIQUEMENT sur les documents internes fournis.

RÈGLES ABSOLUES :
1. Réponds UNIQUEMENT en te basant sur le CONTEXTE fourni ci-dessous.
2. Si l'information n'est pas dans le contexte, réponds EXACTEMENT : "Je ne trouve pas cette information dans les documents disponibles. Veuillez consulter votre responsable ou les ressources humaines."
3. Ne génère JAMAIS d'informations de ta propre connaissance.
4. Ne fais JAMAIS de suppositions ou d'extrapolations.
5. Cite toujours la source du document quand tu réponds.
6. Réponds toujours en français.
7. Sois précis, concis et professionnel.

CONTEXTE EXTRAIT DES DOCUMENTS :
{context}
"""

HUMAN_PROMPT = """Question : {question}

Réponds de manière précise et professionnelle en te basant uniquement sur le contexte fourni."""


class RAGChain:

    def __init__(self, vector_store_manager: VectorStoreManager):
        """
        Initialise la chaîne RAG avec un mode OpenAI ou un mode local de secours.
        """
        self.vector_store_manager = vector_store_manager
        self.retriever = vector_store_manager.get_retriever()
        self.use_fallback_llm = settings.use_local_fallback or not settings.has_openai_credentials

        if self.use_fallback_llm:
            self.llm = None
            self.prompt = None
            self.chain = None
            logger.warning(
                "Aucune clé OpenAI valide détectée — activation du mode de secours local "
                "pour la génération de réponse."
            )
        else:
            self.llm = ChatOpenAI(
                model=settings.llm_model,
                temperature=settings.llm_temperature,
                api_key=settings.openai_api_key,
                max_tokens=1500
            )

            self.prompt = ChatPromptTemplate.from_messages([
                ("system", SYSTEM_PROMPT),
                ("human", HUMAN_PROMPT)
            ])

            self.chain = self._build_chain()

        logger.info(
            f"RAGChain initialisée — "
            f"modèle: {settings.llm_model} — "
            f"fallback: {'oui' if self.use_fallback_llm else 'non'}"
        )

    def _build_chain(self):
        """
        Construit la chaîne RAG standard OpenAI.
        """
        def format_context(docs: list[Document]) -> str:
            """
            Formate les chunks récupérés en contexte structuré.
            Chaque chunk est clairement délimité avec sa source.
            """
            formatted = []
            for i, doc in enumerate(docs, 1):
                source = doc.metadata.get("source", "Document inconnu")
                page = doc.metadata.get("page", "")
                section = doc.metadata.get("heading", "")

                # Construire la référence de source
                if page:
                    source_ref = f"{source} (page {page})"
                elif section:
                    source_ref = f"{source} (section: {section})"
                else:
                    source_ref = source

                formatted.append(
                    f"[Source {i}: {source_ref}]\n{doc.page_content}"
                )

            return "\n\n---\n\n".join(formatted)

        chain = (
            RunnableParallel({
                "context": self.retriever | format_context,
                "question": RunnablePassthrough()
            })
            | self.prompt
            | self.llm
            | StrOutputParser()
        )

        return chain

    def _fallback_answer(self, question: str, retrieved_docs: list[Document]) -> str:
        """
        Génère une réponse locale basée uniquement sur les documents récupérés.
        """
        if not retrieved_docs:
            return (
                "Je ne trouve pas cette information dans les documents disponibles. "
                "Veuillez consulter votre responsable ou les ressources humaines."
            )

        best_doc = retrieved_docs[0]
        source = best_doc.metadata.get("source", "Document inconnu")
        page = best_doc.metadata.get("page")
        context_text = best_doc.page_content.strip()

        if len(context_text) > 600:
            context_text = context_text[:600].rstrip() + "..."

        if page:
            source_label = f"{source} (page {page})"
        else:
            source_label = source

        answer = (
            f"D’après le document '{source_label}', le contexte disponible indique :\n\n"
            f"{context_text}\n\n"
            f"Si vous souhaitez une réponse plus précise, ajoutez plus de détails à votre question et "
            f"assurez-vous qu’un document pertinent ait été ajouté au service."
        )

        return answer

    def invoke(self, question: str) -> dict:
        """
        Exécute la chaîne RAG complète.
        Retourne la réponse + les sources utilisées.
        """
        try:
            # Récupérer les chunks pertinents pour les sources
            retrieved_docs = self.retriever.invoke(question)

            if self.use_fallback_llm:
                # Mode de secours : générer une réponse à partir du contexte local
                answer = self._fallback_answer(question, retrieved_docs)
            else:
                # Mode normal : utiliser la chaîne RAG complète
                answer = self.chain.invoke(question)

            # Construire les sources pour la réponse API
            sources = []
            seen_sources = set()

            for doc in retrieved_docs:
                source_key = f"{doc.metadata.get('source')}_{doc.metadata.get('page', doc.metadata.get('section', ''))}"

                if source_key not in seen_sources:
                    seen_sources.add(source_key)
                    sources.append({
                        "content": doc.page_content[:200] + "..."
                        if len(doc.page_content) > 200
                        else doc.page_content,
                        "source": doc.metadata.get("source", "Inconnu"),
                        "page": doc.metadata.get("page"),
                    })

            logger.info(
                f"Question traitée — "
                f"{len(retrieved_docs)} chunks récupérés — "
                f"réponse générée"
            )

            return {
                "answer": answer,
                "sources": sources
            }

        except Exception as e:
            logger.error(f"Erreur RAG chain : {e}")
            raise