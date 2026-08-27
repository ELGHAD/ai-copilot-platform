package com.alten.chat_service.exception;

/**
 * Levée quand le RAG service (Python/FastAPI) est inaccessible
 * ou retourne une erreur HTTP.
 * Interceptée par GlobalExceptionHandler → 503 SERVICE_UNAVAILABLE.
 */
public class RagServiceException extends RuntimeException {

    public RagServiceException(String message) {
        super(message);
    }

    public RagServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}