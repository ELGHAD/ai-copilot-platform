package com.alten.chat_service.model;

/**
 * Represents the author of a message in a conversation.
 * USER messages are questions; ASSISTANT messages are RAG-generated answers.
 */
public enum MessageRole {
    USER,
    ASSISTANT
}