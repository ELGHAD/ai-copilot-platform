package com.alten.chat_service.service;

import com.alten.chat_service.config.TicketCategorySchemas;
import com.alten.chat_service.dto.*;
import com.alten.chat_service.exception.TicketNotFoundException;
import com.alten.chat_service.exception.UnauthorizedTicketAccessException;
import com.alten.chat_service.model.*;
import com.alten.chat_service.repository.TicketActivityRepository;
import com.alten.chat_service.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository ticketActivityRepository;
    private final FileStorageService fileStorageService;
    private final TicketCategorySchemas ticketCategorySchemas; // ← nouvelle injection

    public TicketResponse createTicket(TicketRequest request, String userEmail, String userRole) {

        Ticket ticket = new Ticket();
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setConversationId(request.getConversationId());
        ticket.setMessageId(request.getMessageId());
        ticket.setRequesterEmail(userEmail);
        ticket.setRequesterName(request.getRequesterName());
        ticket.setRequesterRole(userRole);
        ticket.setTicketType(request.getTicketType());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setSubject(request.getSubject());

        if (request.getTicketType() == TicketType.FREE_TEXT) {
            ticket.setFreeTextContent(request.getFreeTextContent());
        } else {
            // Validation dynamique contre le schema de la catégorie
            List<TicketCategorySchemas.FieldRule> schema = ticketCategorySchemas.getSchema(request.getTicketType());
            Map<String, Object> formData = request.getFormData() != null ? request.getFormData() : Map.of();

            for (TicketCategorySchemas.FieldRule rule : schema) {
                Object value = formData.get(rule.key());
                if (rule.required() && (value == null || value.toString().isBlank())) {
                    throw new IllegalArgumentException("Champ requis manquant : " + rule.label());
                }
            }
            ticket.setFormData(formData);
        }

        Ticket saved = ticketRepository.save(ticket);

        TicketActivity creationActivity = new TicketActivity();
        creationActivity.setTicketId(saved.getId());
        creationActivity.setSenderEmail(userEmail);
        creationActivity.setSenderRole(SenderRole.SYSTEM);
        creationActivity.setContent("Ticket " + saved.getTicketNumber() + " created");
        ticketActivityRepository.save(creationActivity);

        return toResponse(saved);
    }
    // ... reste de la classe inchangé, sauf toResponse() ci-dessous

    /**
     * Returns tickets visible to the given user:
     * - ADMIN sees all tickets
     * - EXPERT/OPERATIONNEL sees only their own tickets
     */
    public List<TicketSummary> getTickets(String userEmail, String userRole) {
        List<Ticket> tickets = isAdmin(userRole)
                ? ticketRepository.findAllByOrderByCreatedAtDesc()
                : ticketRepository.findByRequesterEmailOrderByCreatedAtDesc(userEmail);

        return tickets.stream().map(this::toSummary).collect(Collectors.toList());
    }

    /**
     * Returns the full detail (including timeline) of a single ticket.
     * Access control: owner or ADMIN only.
     */
    public TicketResponse getTicketDetail(Long ticketId, String userEmail, String userRole) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        checkAccess(ticket, userEmail, userRole);

        return toResponse(ticket);
    }

    /**
     * Adds a new message to a ticket's timeline (used by both user and admin replies).
     * Accepts text content and/or an image attachment — at least one of the two is required.
     */
    public TicketActivityResponse addActivity(Long ticketId, TicketActivityRequest request,
                                              String userEmail, String userRole) throws IOException {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        checkAccess(ticket, userEmail, userRole);

        boolean hasContent = request.getContent() != null && !request.getContent().isBlank();
        boolean hasAttachment = request.getAttachment() != null && !request.getAttachment().isEmpty();

        if (!hasContent && !hasAttachment) {
            throw new IllegalArgumentException("Un message ou une pièce jointe est requis");
        }

        String attachmentUrl = hasAttachment
                ? fileStorageService.store(request.getAttachment(), ticketId)
                : null;

        TicketActivity activity = new TicketActivity();
        activity.setTicketId(ticketId);
        activity.setSenderEmail(userEmail);
        activity.setSenderRole(isAdmin(userRole) ? SenderRole.ADMIN : SenderRole.USER);
        activity.setContent(hasContent ? request.getContent() : null);
        activity.setAttachmentUrl(attachmentUrl);

        TicketActivity saved = ticketActivityRepository.save(activity);

        // Admin replying to an OPEN ticket automatically moves it to IN_PROGRESS
        if (isAdmin(userRole) && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            ticketRepository.save(ticket);
        }

        return toActivityResponse(saved);
    }

    /**
     * Updates the status of a ticket. ADMIN only.
     */
    public TicketResponse updateStatus(Long ticketId, TicketStatusUpdateRequest request, String userRole) {
        if (!isAdmin(userRole)) {
            throw new UnauthorizedTicketAccessException("Only admins can change ticket status");
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        ticket.setStatus(request.getStatus());
        if (request.getStatus() == TicketStatus.CLOSED) {
            ticket.setClosedAt(java.time.LocalDateTime.now());
        }

        Ticket saved = ticketRepository.save(ticket);
        return toResponse(saved);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private boolean isAdmin(String role) {
        return ADMIN_ROLE.equals(role);
    }

    private void checkAccess(Ticket ticket, String userEmail, String userRole) {
        boolean isOwner = ticket.getRequesterEmail().equals(userEmail);
        if (!isOwner && !isAdmin(userRole)) {
            throw new UnauthorizedTicketAccessException("You do not have access to this ticket");
        }
    }

    private String generateTicketNumber() {
        long nextCount = ticketRepository.countAllTickets() + 1;
        return String.format("TCK%04d", nextCount);
    }

    private TicketResponse toResponse(Ticket t) {
        TicketResponse r = new TicketResponse();

        r.setId(t.getId());
        r.setTicketNumber(t.getTicketNumber());
        r.setConversationId(t.getConversationId());
        r.setMessageId(t.getMessageId());
        r.setRequesterEmail(t.getRequesterEmail());
        r.setRequesterName(t.getRequesterName());
        r.setRequesterRole(t.getRequesterRole());
        r.setTicketType(t.getTicketType());
        r.setStatus(t.getStatus());
        r.setSubject(t.getSubject());
        r.setFreeTextContent(t.getFreeTextContent());
        r.setFormData(t.getFormData());
        r.setAssignedAdminEmail(t.getAssignedAdminEmail());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        r.setClosedAt(t.getClosedAt());

        List<TicketActivity> activities = ticketActivityRepository.findByTicketIdOrderByCreatedAtAsc(t.getId());
        r.setActivities(activities.stream().map(this::toActivityResponse).collect(Collectors.toList()));

        return r;
    }

    private TicketSummary toSummary(Ticket t) {
        TicketSummary s = new TicketSummary();
        s.setId(t.getId());
        s.setTicketNumber(t.getTicketNumber());
        s.setRequesterName(t.getRequesterName());
        s.setRequesterEmail(t.getRequesterEmail());
        s.setTicketType(t.getTicketType());
        s.setStatus(t.getStatus());
        s.setSubject(t.getSubject());
        s.setCreatedAt(t.getCreatedAt());
        s.setUpdatedAt(t.getUpdatedAt());
        return s;
    }

    private TicketActivityResponse toActivityResponse(TicketActivity a) {
        TicketActivityResponse r = new TicketActivityResponse();
        r.setId(a.getId());
        r.setSenderEmail(a.getSenderEmail());
        r.setSenderRole(a.getSenderRole());
        r.setContent(a.getContent());
        r.setAttachmentUrl(a.getAttachmentUrl());
        r.setCreatedAt(a.getCreatedAt());
        return r;
    }
}