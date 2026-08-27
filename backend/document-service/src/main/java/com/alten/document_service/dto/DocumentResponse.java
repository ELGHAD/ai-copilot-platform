package com.alten.document_service.dto;

import com.alten.document_service.model.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Returned to the client after upload or when fetching document metadata.
 * Never exposes the internal file path for security reasons.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    private Long id;
    private String title;
    private String description;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String roleAccess;
    private DocumentStatus status;
    private String uploadedBy;
    private Integer version;
    private boolean indexed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}