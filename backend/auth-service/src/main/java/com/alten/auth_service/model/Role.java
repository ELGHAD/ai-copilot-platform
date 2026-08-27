package com.alten.auth_service.model;

/**
 * Represents the access level of a user in the system.
 * Each role determines the RAG prompt style and accessible document chunks.
 */
public enum Role {
    ADMIN,
    OPERATIONNEL,
    EXPERT
}