package com.alten.document_service.service;

import com.alten.document_service.dto.DocumentResponse;
import com.alten.document_service.dto.DocumentSummary;
import com.alten.document_service.dto.DocumentUpdateRequest;
import com.alten.document_service.dto.DocumentUploadRequest;
import com.alten.document_service.model.Document;
import com.alten.document_service.model.DocumentStatus;
import com.alten.document_service.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic for document management.
 * Handles file storage on disk and metadata persistence in PostgreSQL.
 * File validation ensures only PDF and Word documents are accepted.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final RestTemplate restTemplate;

    @Value("${document.upload.path}")
    private String uploadPath;

    @Value("${rag.service.url}")
    private String ragServiceUrl;

    /** Allowed MIME types — PDF and Word documents only */
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    /** Maximum file size: 10MB in bytes */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /**
     * Uploads a document file and persists its metadata.
     * Validates file type and size before storing.
     *
     * @param file      the uploaded file (PDF or Word)
     * @param request   metadata payload (title, description, roleAccess)
     * @param uploaderEmail email of the authenticated user performing the upload
     * @return DocumentResponse with the saved document metadata
     * @throws IllegalArgumentException if the file type or size is invalid
     * @throws RuntimeException         if the file cannot be saved to disk
     */
    public DocumentResponse uploadDocument(
            MultipartFile file,
            DocumentUploadRequest request,
            String uploaderEmail) {

        validateFile(file);

        String storedFileName = generateStoredFileName(file.getOriginalFilename());
        Path filePath = saveFileToDisk(file, storedFileName);

        Document document = Document.builder()
                .originalFileName(file.getOriginalFilename())
                .storedFileName(storedFileName)
                .filePath(filePath.toString())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .title(request.getTitle())
                .description(request.getDescription())
                .roleAccess(normalizeRoleAccess(request.getRoleAccess()))
                .uploadedBy(uploaderEmail)
                .build();

        documentRepository.save(document);

        try {
            indexDocumentInRag(filePath, file, document.getRoleAccess());
            document.setIndexed(true);
            documentRepository.save(document);
        } catch (Exception e) {
            document.setIndexed(false);
            documentRepository.save(document);
        }

        return toDocumentResponse(document);
    }

    /**
     * Returns document statistics for the admin dashboard.
     *
     * @return map of statistic name to count
     */
    public Map<String, Long> getDocumentStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalDocuments", documentRepository.count());
        stats.put("activeDocuments", (long) documentRepository.findByStatus(DocumentStatus.ACTIVE).size());
        stats.put("archivedDocuments", (long) documentRepository.findByStatus(DocumentStatus.ARCHIVED).size());
        stats.put("indexedDocuments", (long) documentRepository.findByIndexedFalseAndStatus(DocumentStatus.ACTIVE).size());
        return stats;
    }

    private void indexDocumentInRag(Path filePath, MultipartFile file, String roleAccess) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(filePath));
        body.add("role_access", normalizeRoleAccess(roleAccess));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        restTemplate.postForObject(ragServiceUrl + "/documents/upload", requestEntity, String.class);
    }

    private String normalizeRoleAccess(String roleAccess) {
        if (roleAccess == null || roleAccess.isBlank()) {
            return "COMMUN";
        }

        String normalized = roleAccess.trim().toUpperCase();
        if (normalized.equals("ROLE_ADMIN") || normalized.equals("ADMIN")) return "ADMIN";
        if (normalized.equals("ROLE_EXPERT") || normalized.equals("EXPERT")) return "EXPERT";
        if (normalized.equals("ROLE_OPERATIONNEL") || normalized.equals("OPERATIONNEL")) return "OPERATIONNEL";
        if (normalized.equals("COMMUN")) return "COMMUN";
        return "COMMUN";
    }

    /**
     * Retrieves metadata for a specific document by ID.
     *
     * @param id the document's database ID
     * @return DocumentResponse with the document metadata
     * @throws IllegalStateException if no document is found for the given ID
     */
    public DocumentResponse getDocumentById(Long id) {
        Document document = findDocumentByIdOrThrow(id);
        return toDocumentResponse(document);
    }

    /**
     * Retrieves a summary list of all documents.
     * Restricted to ADMIN role.
     *
     * @return list of DocumentSummary for all documents
     */
    public List<DocumentSummary> getAllDocuments() {
        return documentRepository.findAll()
                .stream()
                .map(this::toDocumentSummary)
                .toList();
    }

    /**
     * Retrieves all documents with a specific lifecycle status.
     *
     * @param status the document lifecycle status to filter by
     * @return list of DocumentSummary matching the given status
     */
    public List<DocumentSummary> getDocumentsByStatus(DocumentStatus status) {
        return documentRepository.findByStatus(status)
                .stream()
                .map(this::toDocumentSummary)
                .toList();
    }

    /**
     * Updates a document's metadata.
     * Restricted to ADMIN role.
     *
     * @param id      the document's database ID
     * @param request update payload containing title, description, roleAccess, status
     * @return updated DocumentResponse
     * @throws IllegalStateException if no document is found for the given ID
     */
    public DocumentResponse updateDocument(Long id, DocumentUpdateRequest request) {
        Document document = findDocumentByIdOrThrow(id);

        document.setTitle(request.getTitle());
        document.setDescription(request.getDescription());
        document.setRoleAccess(normalizeRoleAccess(request.getRoleAccess()));
        document.setStatus(request.getStatus());

        // Reset indexed flag when metadata changes — RAG service must re-index
        document.setIndexed(false);

        documentRepository.save(document);
        return toDocumentResponse(document);
    }

    /**
     * Marks a document as ARCHIVED and removes the file from disk.
     * Restricted to ADMIN role.
     *
     * @param id the document's database ID
     * @throws IllegalStateException if no document is found for the given ID
     */
    public void deleteDocument(Long id) {
        Document document = findDocumentByIdOrThrow(id);
        document.setStatus(DocumentStatus.ARCHIVED);
        documentRepository.save(document);
        deleteFileFromDisk(document.getFilePath());
    }

    /**
     * Marks a document as indexed after the RAG service has processed it.
     * Called internally — not exposed as a public REST endpoint.
     *
     * @param id the document's database ID
     * @throws IllegalStateException if no document is found for the given ID
     */
    public void markAsIndexed(Long id) {
        Document document = findDocumentByIdOrThrow(id);
        document.setIndexed(true);
        documentRepository.save(document);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Validates the uploaded file's content type and size.
     *
     * @throws IllegalArgumentException if the file type or size is invalid
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Invalid file type. Only PDF and Word documents are allowed");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds the maximum allowed size of 10MB");
        }
    }

    /**
     * Generates a unique stored filename using UUID to prevent collisions.
     *
     * @param originalFileName the original filename from the upload
     * @return a unique filename preserving the original extension
     */
    private String generateStoredFileName(String originalFileName) {
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * Saves the uploaded file to disk in the configured upload directory.
     *
     * @param file           the uploaded file
     * @param storedFileName the unique filename to use for storage
     * @return the full path where the file was saved
     * @throws RuntimeException if the file cannot be written to disk
     */
    private Path saveFileToDisk(MultipartFile file, String storedFileName) {
        try {
            Path uploadDir = Paths.get(uploadPath);
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(storedFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            return filePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file to disk: " + e.getMessage());
        }
    }

    /**
     * Deletes a file from disk silently — does not throw if the file is missing.
     *
     * @param filePath the full path of the file to delete
     */
    private void deleteFileFromDisk(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            // Log but do not throw — file may already be missing
        }
    }

    /**
     * Finds a document by ID or throws a descriptive exception.
     */
    private Document findDocumentByIdOrThrow(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Document not found with id: " + id));
    }

    /**
     * Maps a Document entity to a full DocumentResponse DTO.
     */
    private DocumentResponse toDocumentResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .originalFileName(document.getOriginalFileName())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .roleAccess(document.getRoleAccess())
                .status(document.getStatus())
                .uploadedBy(document.getUploadedBy())
                .version(document.getVersion())
                .indexed(document.isIndexed())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    /**
     * Maps a Document entity to a lightweight DocumentSummary DTO.
     */
    private DocumentSummary toDocumentSummary(Document document) {
        return DocumentSummary.builder()
                .id(document.getId())
                .title(document.getTitle())
                .originalFileName(document.getOriginalFileName())
                .roleAccess(document.getRoleAccess())
                .status(document.getStatus())
                .uploadedBy(document.getUploadedBy())
                .version(document.getVersion())
                .indexed(document.isIndexed())
                .createdAt(document.getCreatedAt())
                .build();
    }
}