package com.alten.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Returned to the client after the RAG pipeline generates an answer.
 * Contains the answer, cited sources, confidence score, and conversation context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private Long conversationId;
    private Long messageId;
    private String answer;
    private List<SourceReference> sources;
    private Double confidenceScore;
    private LocalDateTime createdAt;
}