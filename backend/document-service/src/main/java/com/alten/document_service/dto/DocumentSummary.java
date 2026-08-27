package com.alten.document_service.dto;

import com.alten.document_service.model.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight document representation used in list views.
 * Contains only the fields needed for display in the admin dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSummary {

    private Long id;
    private String title;
    private String originalFileName;
    private String roleAccess;
    private DocumentStatus status;
    private String uploadedBy;
    private Integer version;
    private boolean indexed;
    private LocalDateTime createdAt;
}