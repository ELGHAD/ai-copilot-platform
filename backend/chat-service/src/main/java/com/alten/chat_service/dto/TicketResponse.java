package com.alten.chat_service.dto;

import com.alten.chat_service.model.TicketStatus;
import com.alten.chat_service.model.TicketType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class TicketResponse {

    private Long id;
    private String ticketNumber;
    private Long conversationId;
    private Long messageId;
    private String requesterEmail;
    private String requesterName;
    private String requesterRole;
    private TicketType ticketType;
    private TicketStatus status;
    private String subject;

    private String freeTextContent;

    // Remplace departmentProjectTeam, approverName, softwareFullName, installationSource,
    // softwarePublisher, exactVersionRequired, businessJustification, primaryPurpose, adminPrivilegeRequired
    private Map<String, Object> formData;

    private String assignedAdminEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;

    private List<TicketActivityResponse> activities;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    public String getRequesterEmail() { return requesterEmail; }
    public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public String getRequesterRole() { return requesterRole; }
    public void setRequesterRole(String requesterRole) { this.requesterRole = requesterRole; }

    public TicketType getTicketType() { return ticketType; }
    public void setTicketType(TicketType ticketType) { this.ticketType = ticketType; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getFreeTextContent() { return freeTextContent; }
    public void setFreeTextContent(String freeTextContent) { this.freeTextContent = freeTextContent; }

    public Map<String, Object> getFormData() { return formData; }
    public void setFormData(Map<String, Object> formData) { this.formData = formData; }

    public String getAssignedAdminEmail() { return assignedAdminEmail; }
    public void setAssignedAdminEmail(String assignedAdminEmail) { this.assignedAdminEmail = assignedAdminEmail; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public List<TicketActivityResponse> getActivities() { return activities; }
    public void setActivities(List<TicketActivityResponse> activities) { this.activities = activities; }
}