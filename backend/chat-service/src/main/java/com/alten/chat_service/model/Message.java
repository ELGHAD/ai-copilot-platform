package com.alten.chat_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a single message in a conversation.
 * Can be authored by the USER or the ASSISTANT (RAG-generated).
 * Stores the answer sources and confidence score for ASSISTANT messages.
 */
@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The conversation this message belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /** Whether this message was written by the user or the AI */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    /** The message content — question or AI-generated answer */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * JSON string containing the sources cited in the answer.
     * Format: [{"documentTitle": "...", "page": 1, "excerpt": "..."}]
     * Null for USER messages.
     */
    @Column(columnDefinition = "TEXT")
    private String sources;

    /**
     * Confidence score of the RAG answer between 0 and 1.
     * Null for USER messages.
     */
    @Column
    private Double confidenceScore;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}