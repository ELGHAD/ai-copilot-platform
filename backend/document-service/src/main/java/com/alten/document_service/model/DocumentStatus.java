package com.alten.document_service.model;

/**
 * Represents the lifecycle state of a document in the knowledge base.
 * Controls visibility in RAG retrieval and admin dashboard display.
 */
public enum DocumentStatus {
    /** Document is active and included in RAG retrieval */
    ACTIVE,
    /** Document is outdated and excluded from RAG retrieval */
    OBSOLETE,
    /** Document is archived — kept for audit but not indexed */
    ARCHIVED
}