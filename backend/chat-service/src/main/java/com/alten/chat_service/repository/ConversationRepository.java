package com.alten.chat_service.repository;

import com.alten.chat_service.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access layer for Conversation entities.
 * Provides queries for fetching conversations by user and ordering by recency.
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Finds all conversations for a specific user ordered by most recent first.
     * Used to display the user's conversation history in the chat interface.
     *
     * @param userEmail the email of the user
     * @return list of conversations ordered by updatedAt descending
     */
    List<Conversation> findByUserEmailOrderByUpdatedAtDesc(String userEmail);

    /**
     * Counts the total number of conversations for a specific user.
     * Used for admin analytics.
     *
     * @param userEmail the email of the user
     * @return total number of conversations for the user
     */
    long countByUserEmail(String userEmail);
}