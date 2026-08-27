package com.alten.document_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an internal document uploaded to the knowledge base.
 * Stores metadata only — the actual file is stored on disk.
 * The RAG service reads this table to know which documents to index.
 */
@Entity
@Table(name = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Original filename as uploaded by the user */
    @Column(nullable = false)
    private String originalFileName;

    /** Sanitized filename used for storage on disk */
    @Column(nullable = false, unique = true)
    private String storedFileName;

    /** File path on disk relative to the upload directory */
    @Column(nullable = false)
    private String filePath;

    /** MIME type — used to validate and identify the file format */
    @Column(nullable = false)
    private String contentType;

    /** File size in bytes */
    @Column(nullable = false)
    private Long fileSize;

    /** Human-readable title for display in the admin dashboard */
    @Column(nullable = false)
    private String title;

    /** Optional description of the document's content */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Minimum role required to access chunks from this document.
     * Enforced by the RAG service during retrieval.
     */
    @Column(nullable = false)
    private String roleAccess;

    /** Current lifecycle state of the document */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    /** Email of the user who uploaded the document */
    @Column(nullable = false)
    private String uploadedBy;

    /** Document version — incremented on re-upload */
    @Column(nullable = false)
    private Integer version;

    /** True if the RAG service has indexed this document */
    @Column(nullable = false)
    private boolean indexed;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = DocumentStatus.ACTIVE;
        this.version = 1;
        this.indexed = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}