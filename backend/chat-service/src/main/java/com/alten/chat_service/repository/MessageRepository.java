package com.alten.chat_service.repository;

import com.alten.chat_service.model.Message;
import com.alten.chat_service.model.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access layer for Message entities.
 * Provides queries for fetching messages by conversation and role.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Finds all messages in a specific conversation ordered by creation time.
     * Used to reconstruct the full conversation history.
     *
     * @param conversationId the conversation's database ID
     * @return list of messages ordered by createdAt ascending
     */
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /**
     * Counts total messages by role across all conversations.
     * Used for admin analytics — tracks total questions asked.
     *
     * @param role the message role (USER or ASSISTANT)
     * @return total count of messages with the given role
     */
    long countByRole(MessageRole role);

    /**
     * Counts total messages in a specific conversation.
     * Used to avoid lazy loading the full messages list.
     *
     * @param conversationId the conversation's database ID
     * @return total number of messages in the conversation
     */
    long countByConversationId(Long conversationId);
}