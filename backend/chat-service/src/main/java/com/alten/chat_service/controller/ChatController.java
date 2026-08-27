package com.alten.chat_service.controller;

import com.alten.chat_service.dto.ChatRequest;
import com.alten.chat_service.dto.ChatResponse;
import com.alten.chat_service.dto.ConversationSummary;
import com.alten.chat_service.dto.MessageResponse;
import com.alten.chat_service.security.JwtUtil;
import com.alten.chat_service.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing chat endpoints.
 *
 * Access rules:
 * - POST   /api/chat/ask                          → any authenticated user
 * - GET    /api/chat/conversations                → any authenticated user (own conversations)
 * - GET    /api/chat/conversations/{id}/messages  → any authenticated user (own messages)
 * - DELETE /api/chat/conversations/{id}           → any authenticated user (own conversation)
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final JwtUtil jwtUtil;

    /**
     * Sends a question to the RAG pipeline and returns the AI-generated answer.
     * Creates a new conversation if no conversationId is provided in the request.
     *
     * POST /api/chat/ask
     *
     * @param request        validated chat request containing the question and optional conversationId
     * @param authentication injected by Spring Security from the JWT filter
     * @return 201 CREATED with ChatResponse containing the answer and sources
     */
    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> ask(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) {

        String userEmail = authentication.getName();
        String token = authHeader.substring(7);
        String userRole = jwtUtil.extractRole(token);

        ChatResponse response = chatService.ask(request, userEmail, userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns all conversations for the authenticated user ordered by most recent first.
     *
     * GET /api/chat/conversations
     *
     * @param authentication injected by Spring Security from the JWT filter
     * @return 200 OK with list of ConversationSummary
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummary>> getUserConversations(
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(chatService.getUserConversations(userEmail));
    }

    /**
     * Returns the full message history of a specific conversation.
     * Only the owner of the conversation can access it.
     *
     * GET /api/chat/conversations/{id}/messages
     *
     * @param id             the conversation's database ID
     * @param authentication injected by Spring Security from the JWT filter
     * @return 200 OK with list of MessageResponse ordered by creation time
     */
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<MessageResponse>> getConversationMessages(
            @PathVariable Long id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(chatService.getConversationMessages(id, userEmail));
    }

    /**
     * Deletes a conversation and all its messages.
     * Only the owner of the conversation can delete it.
     *
     * DELETE /api/chat/conversations/{id}
     *
     * @param id             the conversation's database ID
     * @param authentication injected by Spring Security from the JWT filter
     * @return 204 NO CONTENT on success
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long id,
            Authentication authentication) {
        String userEmail = authentication.getName();
        chatService.deleteConversation(id, userEmail);
        return ResponseEntity.noContent().build();
    }
}