package com.alten.chat_service.dto;

import com.alten.chat_service.model.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class TicketRequest {

    private Long conversationId;
    private Long messageId;

    @NotNull(message = "Ticket type is required")
    private TicketType ticketType;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Requester name is required")
    private String requesterName;

    // --- FREE_TEXT ---
    private String freeTextContent;

    // --- Tous les formulaires structurés passent ici ---
    private Map<String, Object> formData;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public TicketType getTicketType() { return ticketType; }
    public void setTicketType(TicketType ticketType) { this.ticketType = ticketType; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getFreeTextContent() { return freeTextContent; }
    public void setFreeTextContent(String freeTextContent) { this.freeTextContent = freeTextContent; }
    public Map<String, Object> getFormData() { return formData; }
    public void setFormData(Map<String, Object> formData) { this.formData = formData; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
}