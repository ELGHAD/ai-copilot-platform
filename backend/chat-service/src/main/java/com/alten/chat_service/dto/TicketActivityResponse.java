package com.alten.chat_service.dto;

import com.alten.chat_service.model.SenderRole;
import java.time.LocalDateTime;

public class TicketActivityResponse {

    private Long id;
    private String senderEmail;
    private SenderRole senderRole;
    private String content;
    private String attachmentUrl;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public SenderRole getSenderRole() { return senderRole; }
    public void setSenderRole(SenderRole senderRole) { this.senderRole = senderRole; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}