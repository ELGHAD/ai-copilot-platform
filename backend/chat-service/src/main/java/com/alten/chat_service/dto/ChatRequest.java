package com.alten.chat_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload received when a user sends a question to the AI Copilot.
 * Contains the question and the conversation context (if continuing an existing one).
 */
@Data
public class ChatRequest {

    @NotBlank(message = "Question is required")
    private String question;

    /**
     * ID of an existing conversation to continue.
     * If null, a new conversation is created automatically.
     */
    private Long conversationId;
}