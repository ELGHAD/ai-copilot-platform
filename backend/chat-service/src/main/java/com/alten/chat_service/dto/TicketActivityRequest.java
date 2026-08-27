package com.alten.chat_service.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * Payload for POST /api/tickets/{id}/activities.
 * Bound via @ModelAttribute since the endpoint now consumes multipart/form-data
 * (text reply and/or an image attachment sent together, like a real chat).
 *
 * Validation note: content is intentionally NOT @NotBlank anymore — a user can
 * send an attachment alone. The "at least one of content/attachment" rule is
 * enforced in TicketService.addActivity().
 */
public class TicketActivityRequest {

    private String content;

    private MultipartFile attachment;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public MultipartFile getAttachment() { return attachment; }
    public void setAttachment(MultipartFile attachment) { this.attachment = attachment; }
}