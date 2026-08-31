-- Runs once, on first initialisation of the rag_db volume.
-- langchain-postgres expects the pgvector extension to exist before it creates
-- its collection/embedding tables.
CREATE EXTENSION IF NOT EXISTS vector;
