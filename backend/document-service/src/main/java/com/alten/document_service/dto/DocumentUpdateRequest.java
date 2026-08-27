package com.alten.document_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import com.alten.document_service.model.DocumentStatus;

/**
 * Payload received when an ADMIN updates document metadata.
 * Does not allow re-uploading the file — only metadata changes.
 */
@Data
public class DocumentUpdateRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Role access is required")
    @Pattern(
            regexp = "ADMIN|EXPERT|OPERATIONNEL",
            message = "Role access must be ADMIN, EXPERT, or OPERATIONNEL"
    )
    private String roleAccess;

    @NotNull(message = "Status is required")
    private DocumentStatus status;
}