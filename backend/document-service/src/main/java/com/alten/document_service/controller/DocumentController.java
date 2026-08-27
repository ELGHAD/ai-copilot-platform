package com.alten.document_service.controller;

import com.alten.document_service.dto.DocumentResponse;
import com.alten.document_service.dto.DocumentSummary;
import com.alten.document_service.dto.DocumentUpdateRequest;
import com.alten.document_service.dto.DocumentUploadRequest;
import com.alten.document_service.model.DocumentStatus;
import com.alten.document_service.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing document management endpoints.
 *
 * Access rules:
 * - POST   /api/documents              → ADMIN only (upload)
 * - GET    /api/documents              → any authenticated user (list all)
 * - GET    /api/documents/{id}         → any authenticated user (get by id)
 * - GET    /api/documents/status/{s}   → ADMIN only (filter by status)
 * - PUT    /api/documents/{id}         → ADMIN only (update metadata)
 * - DELETE /api/documents/{id}         → ADMIN only (archive)
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Uploads a new document to the knowledge base.
     * Accepts multipart/form-data with the file and metadata fields.
     * Restricted to ADMIN role.
     *
     * POST /api/documents
     *
     * @param file          the uploaded PDF or Word file
     * @param title         document title (form field)
     * @param description   optional document description (form field)
     * @param roleAccess    minimum role required to access this document (form field)
     * @param authentication injected by Spring Security from the JWT filter
     * @return 201 CREATED with DocumentResponse containing the saved metadata
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestPart("file") MultipartFile file,
            @RequestPart("title") String title,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart("roleAccess") String roleAccess,
            Authentication authentication) {

        DocumentUploadRequest request = new DocumentUploadRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setRoleAccess(roleAccess);

        String uploaderEmail = authentication.getName();
        DocumentResponse response = documentService.uploadDocument(file, request, uploaderEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns document statistics for the admin dashboard.
     * Restricted to ADMIN role.
     *
     * GET /api/documents/stats
     *
     * @return 200 OK with document statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(documentService.getDocumentStats());
    }

    /**
     * Returns a summary list of all documents.
     * Any authenticated user can see the list.
     *
     * GET /api/documents
     *
     * @return 200 OK with list of all document summaries
     */
    @GetMapping
    public ResponseEntity<List<DocumentSummary>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    /**
     * Returns the full metadata of a specific document by ID.
     * Any authenticated user can fetch document metadata.
     *
     * GET /api/documents/{id}
     *
     * @param id the document's database ID
     * @return 200 OK with the document metadata
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentById(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    /**
     * Returns all documents filtered by lifecycle status.
     * Restricted to ADMIN role.
     *
     * GET /api/documents/status/{status}
     *
     * @param status the lifecycle status to filter by (ACTIVE, OBSOLETE, ARCHIVED)
     * @return 200 OK with list of document summaries matching the status
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DocumentSummary>> getDocumentsByStatus(
            @PathVariable DocumentStatus status) {
        return ResponseEntity.ok(documentService.getDocumentsByStatus(status));
    }

    /**
     * Updates a document's metadata.
     * Restricted to ADMIN role.
     *
     * PUT /api/documents/{id}
     *
     * @param id      the document's database ID
     * @param request validated update payload
     * @return 200 OK with the updated document metadata
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentUpdateRequest request) {
        return ResponseEntity.ok(documentService.updateDocument(id, request));
    }

    /**
     * Archives a document and removes the file from disk.
     * Restricted to ADMIN role.
     *
     * DELETE /api/documents/{id}
     *
     * @param id the document's database ID
     * @return 204 NO CONTENT on success
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}