package com.alten.chat_service.service;

import com.alten.chat_service.dto.*;
import com.alten.chat_service.exception.RagServiceException;
import com.alten.chat_service.model.Conversation;
import com.alten.chat_service.model.Message;
import com.alten.chat_service.model.MessageRole;
import com.alten.chat_service.repository.ConversationRepository;
import com.alten.chat_service.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Business logic for the chat feature.
 * Orchestrates the RAG pipeline by forwarding questions to the Python RAG service,
 * persisting conversation history, and returning structured responses.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final WebClient ragWebClient;  // bean injecté depuis WebClientConfig

    /**
     * Processes a user question through the RAG pipeline.
     * Creates a new conversation if no conversationId is provided.
     * Persists both the user question and the AI answer as messages.
     *
     * @param request    the chat request containing the question and optional conversationId
     * @param userEmail  email of the authenticated user (from JWT)
     * @param userRole   role of the authenticated user (from JWT)
     * @return ChatResponse containing the AI answer, sources, and conversation context
     * @throws RagServiceException if the RAG service is unreachable or returns an error
     */
    public ChatResponse ask(ChatRequest request, String userEmail, String userRole) {
        Conversation conversation = resolveConversation(request, userEmail, userRole);

        // Persist user question
        Message userMessage = buildMessage(conversation, MessageRole.USER,
                request.getQuestion(), null, null);
        messageRepository.save(userMessage);

        // Forward question to RAG service — throws RagServiceException on failure,
        // caught by GlobalExceptionHandler → 503 to the frontend
        RagResponse ragResponse = callRagService(request.getQuestion(), userRole);

        // Persist AI answer with sources
        String sourcesJson = serializeSources(ragResponse.getSources());
        Message assistantMessage = buildMessage(conversation, MessageRole.ASSISTANT,
                ragResponse.getAnswer(), sourcesJson, ragResponse.getConfidenceScore());
        messageRepository.save(assistantMessage);

        // Update conversation timestamp and title
        updateConversation(conversation, request.getQuestion());

        return ChatResponse.builder()
                .conversationId(conversation.getId())
                .messageId(assistantMessage.getId())
                .answer(ragResponse.getAnswer())
                .sources(ragResponse.getSources())
                .confidenceScore(ragResponse.getConfidenceScore())
                .createdAt(assistantMessage.getCreatedAt())
                .build();
    }

    /**
     * Retrieves all conversations for the authenticated user ordered by recency.
     */
    public List<ConversationSummary> getUserConversations(String userEmail) {
        return conversationRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail)
                .stream()
                .map(this::toConversationSummary)
                .toList();
    }

    /**
     * Retrieves the full message history of a specific conversation.
     * Only the owner of the conversation can access it.
     */
    public List<MessageResponse> getConversationMessages(Long conversationId, String userEmail) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Conversation not found with id: " + conversationId));

        if (!conversation.getUserEmail().equals(userEmail)) {
            throw new IllegalStateException("Access denied to this conversation");
        }

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    /**
     * Deletes a conversation and all its messages.
     * Only the owner of the conversation can delete it.
     */
    public void deleteConversation(Long conversationId, String userEmail) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Conversation not found with id: " + conversationId));

        if (!conversation.getUserEmail().equals(userEmail)) {
            throw new IllegalStateException("Access denied to this conversation");
        }

        conversationRepository.delete(conversation);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Resolves the conversation — creates a new one if no conversationId is provided.
     */
    private Conversation resolveConversation(
            ChatRequest request, String userEmail, String userRole) {

        if (request.getConversationId() != null) {
            return conversationRepository.findById(request.getConversationId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Conversation not found with id: " + request.getConversationId()));
        }

        Conversation newConversation = Conversation.builder()
                .userEmail(userEmail)
                .userRole(userRole)
                .title("New conversation")
                .build();

        return conversationRepository.save(newConversation);
    }

    /**
     * Calls the Python RAG service with the user's question and role.
     * Uses the shared singleton WebClient bean (ragWebClient) configured with
     * connect/read/write timeouts in WebClientConfig.
     *
     * Error handling strategy:
     * - 4xx/5xx HTTP response from RAG service → RagServiceException with status + body
     * - Connection refused / timeout / any other failure → RagServiceException wrapping the cause
     * - No more silent fallback: the exception propagates to GlobalExceptionHandler,
     *   which returns a clean 503 to the frontend.
     */
    private RagResponse callRagService(String question, String userRole) {
        try {
            Map<String, String> requestBody = Map.of(
                    "question", question,
                    "role", userRole,
                    "session_id", ""
            );

            // The trailing slash is required. The FastAPI route is registered as
            // "/chat/" (APIRouter prefix "/chat" + path "/"), so a POST to "/chat"
            // gets a 307 redirect. WebClient does not follow redirects by default,
            // so the body came back empty and deserialized to null.
            RagResponse ragResponse = ragWebClient.post()
                    .uri("/chat/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(
                            // 3xx included on purpose: an unfollowed redirect is not an
                            // answer, and surfacing it beats silently producing a null.
                            status -> status.is3xxRedirection()
                                    || status.is4xxClientError()
                                    || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .defaultIfEmpty("no body")
                                    .map(body -> new RagServiceException(
                                            "RAG service returned [" + response.statusCode() + "]: " + body))
                    )
                    .bodyToMono(RagResponse.class)
                    .block();

            // An empty body yields an empty Mono, so block() returns null. Without
            // this guard the null escapes into ask() and NPEs on getSources().
            if (ragResponse == null) {
                throw new RagServiceException(
                        "RAG service returned an empty response body");
            }

            return ragResponse;

        } catch (RagServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new RagServiceException(
                    "RAG service is unreachable: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the conversation's title (from first question) and updatedAt timestamp.
     */
    private void updateConversation(Conversation conversation, String question) {
        if ("New conversation".equals(conversation.getTitle())) {
            String title = question.length() > 60
                    ? question.substring(0, 60) + "..."
                    : question;
            conversation.setTitle(title);
        }
        conversationRepository.save(conversation);
    }

    /**
     * Builds a Message entity ready for persistence.
     */
    private Message buildMessage(
            Conversation conversation,
            MessageRole role,
            String content,
            String sourcesJson,
            Double confidenceScore) {

        return Message.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .sources(sourcesJson)
                .confidenceScore(confidenceScore)
                .build();
    }

    /**
     * Serializes a list of SourceReference objects to a JSON string for storage.
     * Returns null if the list is empty or serialization fails.
     */
    private String serializeSources(List<SourceReference> sources) {
        if (sources == null || sources.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Deserializes a JSON string back to a list of SourceReference objects.
     * Returns an empty list if the string is null or deserialization fails.
     */
    private List<SourceReference> deserializeSources(String sourcesJson) {
        if (sourcesJson == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(sourcesJson,
                    new TypeReference<List<SourceReference>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Maps a Conversation entity to a ConversationSummary DTO.
     * Uses a separate count query to avoid lazy loading issues.
     */
    private ConversationSummary toConversationSummary(Conversation conversation) {
        long messageCount = messageRepository.countByConversationId(conversation.getId());
        return ConversationSummary.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .userEmail(conversation.getUserEmail())
                .userRole(conversation.getUserRole())
                .messageCount((int) messageCount)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    /**
     * Maps a Message entity to a MessageResponse DTO.
     */
    private MessageResponse toMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .sources(deserializeSources(message.getSources()))
                .confidenceScore(message.getConfidenceScore())
                .createdAt(message.getCreatedAt())
                .build();
    }
}