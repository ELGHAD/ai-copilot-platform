package com.alten.chat_service.dto;

import com.alten.chat_service.model.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a single message returned to the client.
 * Used when fetching the full message history of a conversation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private MessageRole role;
    private String content;
    private List<SourceReference> sources;
    private Double confidenceScore;
    private LocalDateTime createdAt;
}