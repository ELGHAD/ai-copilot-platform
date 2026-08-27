package com.alten.user_service.model;

/**
 * Mirrors the Role enum from auth-service.
 * Used to filter RAG responses and adapt the AI prompt style per user type.
 */
public enum Role {
    ADMIN,
    OPERATIONNEL,
    EXPERT
}