# RAG Service

## Purpose

The `rag-service` is the AI layer of the platform. It provides Retrieval-Augmented Generation (RAG) capabilities for internal documents.

Its role is to allow users to ask questions in natural language and receive answers grounded in internal documents rather than generic AI knowledge.

## Main features

- document upload and indexing
- PDF and DOCX parsing
- chunking of extracted content
- embedding generation
- storage in PostgreSQL with PGVector
- semantic retrieval of relevant chunks
- answer generation with an LLM
- source citation for each answer

## Stack

- Python 3
- FastAPI
- Uvicorn
- LangChain
- OpenAI embeddings
- OpenAI GPT model
- PostgreSQL + PGVector
- Pydantic / Pydantic Settings

## Main flow

1. Upload a document
2. Parse the document content
3. Split it into chunks
4. Generate embeddings
5. Store them in the vector database
6. Retrieve relevant chunks for a question
7. Inject those chunks into a controlled prompt
8. Generate a grounded answer with citations

## How to run

```powershell
cd rag-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8085
```

## Configuration

The service uses a configuration file and environment variables via `.env`.

Important settings include:

- `openai_api_key`
- `database_url`
- `embedding_model`
- `llm_model`
- `chunk_size`
- `chunk_overlap`
- `max_retrieved_docs`

## Endpoints

- `GET /health`
- `POST /documents/upload`
- `DELETE /documents/{document_id}`
- `POST /chat/`

## Notes

The service is designed to be conservative and safe: it refuses to answer beyond the provided document context and always cites the document sources used for the response.
