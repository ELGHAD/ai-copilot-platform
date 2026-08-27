from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class DocumentUploadResponse(BaseModel):
    document_id: str
    filename: str
    chunks_count: int
    message: str


class ChatRequest(BaseModel):
    question: str
    role: Optional[str] = "OPERATIONNEL"
    session_id: Optional[str] = None


class SourceDocument(BaseModel):
    content: str
    source: str
    page: Optional[int] = None


class ChatResponse(BaseModel):
    answer: str
    sources: list[SourceDocument]
    session_id: str


class DocumentInfo(BaseModel):
    document_id: str
    filename: str
    uploaded_at: datetime
    chunks_count: int


class DeleteResponse(BaseModel):
    document_id: str
    message: str


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str