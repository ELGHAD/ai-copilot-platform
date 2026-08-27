package com.alten.document_service.repository;

import com.alten.document_service.model.Document;
import com.alten.document_service.model.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for Document entities.
 * Provides queries for filtering by status, role, uploader, and indexing state.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * Finds all documents with a specific lifecycle status.
     * Used by admin dashboard to filter by ACTIVE, OBSOLETE, or ARCHIVED.
     *
     * @param status the document lifecycle status
     * @return list of documents matching the given status
     */
    List<Document> findByStatus(DocumentStatus status);

    /**
     * Finds all documents accessible by a specific role.
     * Used by the RAG service to filter chunks by role_access.
     *
     * @param roleAccess the minimum role required to access the document
     * @return list of documents accessible by the given role
     */
    List<Document> findByRoleAccess(String roleAccess);

    /**
     * Finds all documents uploaded by a specific user.
     * Used by admin dashboard to track document ownership.
     *
     * @param uploadedBy the email of the uploader
     * @return list of documents uploaded by the given user
     */
    List<Document> findByUploadedBy(String uploadedBy);

    /**
     * Finds all documents that have not yet been indexed by the RAG service.
     * Used by the RAG service to pick up new documents for ingestion.
     *
     * @return list of unindexed active documents
     */
    List<Document> findByIndexedFalseAndStatus(DocumentStatus status);

    /**
     * Checks whether a document with the given stored filename already exists.
     * Used to prevent duplicate uploads.
     *
     * @param storedFileName the sanitized filename used for storage
     * @return true if a document with this filename exists
     */
    boolean existsByStoredFileName(String storedFileName);

    /**
     * Finds a document by its stored filename.
     * Used during file deletion to locate the correct record.
     *
     * @param storedFileName the sanitized filename used for storage
     * @return an Optional containing the document if found
     */
    Optional<Document> findByStoredFileName(String storedFileName);
}