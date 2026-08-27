package com.alten.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight conversation representation used in history list views.
 * Contains only the fields needed to display the conversation list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {

    private Long id;
    private String title;
    private String userEmail;
    private String userRole;
    private int messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}