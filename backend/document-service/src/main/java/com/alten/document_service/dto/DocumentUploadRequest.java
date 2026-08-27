package com.alten.document_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Metadata payload sent alongside the file during upload.
 * The actual file is received as MultipartFile in the controller.
 */
@Data
public class DocumentUploadRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    /**
     * Minimum role required to access this document's chunks in RAG retrieval.
     * Must be one of: ADMIN, EXPERT, OPERATIONNEL
     */
    @NotBlank(message = "Role access is required")
    @Pattern(
            regexp = "ADMIN|EXPERT|OPERATIONNEL",
            message = "Role access must be ADMIN, EXPERT, or OPERATIONNEL"
    )
    private String roleAccess;
}