package com.alten.chat_service.controller;

import com.alten.chat_service.config.TicketCategorySchemas;
import com.alten.chat_service.dto.*;
import com.alten.chat_service.model.TicketType;
import com.alten.chat_service.security.JwtUtil;
import com.alten.chat_service.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing ticket endpoints.
 *
 * Access rules:
 * - POST   /api/tickets                      → any authenticated user (EXPERT/OPERATIONNEL/ADMIN)
 * - GET    /api/tickets                      → ADMIN sees all tickets, others see only their own
 * - GET    /api/tickets/{id}                 → owner or ADMIN only
 * - POST   /api/tickets/{id}/activities      → owner or ADMIN only (reply in the timeline, text and/or image)
 * - PATCH  /api/tickets/{id}/status          → ADMIN only
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final JwtUtil jwtUtil;

    private final TicketCategorySchemas ticketCategorySchemas; // ← ajoute dans le constructeur (Lombok @RequiredArgsConstructor s'en charge automatiquement)

    /**
     * Returns the form schema for every ticket category, used by Angular
     * to render the category picker + dynamic form.
     *
     * GET /api/tickets/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<Map<TicketType, List<TicketCategorySchemas.FieldRule>>> getCategories() {
        return ResponseEntity.ok(ticketCategorySchemas.getAllSchemas());
    }
    /**
     * Creates a new ticket (form-based or free text), triggered when a user
     * flags a RAG answer as unsatisfactory.
     *
     * POST /api/tickets
     */
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody TicketRequest request,
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) {

        String userEmail = authentication.getName();
        String token = authHeader.substring(7);
        String userRole = jwtUtil.extractRole(token);

        TicketResponse response = ticketService.createTicket(request, userEmail, userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns the list of tickets visible to the authenticated user.
     * ADMIN gets every ticket; other roles get only their own.
     *
     * GET /api/tickets
     */
    @GetMapping
    public ResponseEntity<List<TicketSummary>> getTickets(
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) {

        String userEmail = authentication.getName();
        String token = authHeader.substring(7);
        String userRole = jwtUtil.extractRole(token);

        return ResponseEntity.ok(ticketService.getTickets(userEmail, userRole));
    }

    /**
     * Returns the full detail of a ticket, including its activity timeline.
     *
     * GET /api/tickets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicketDetail(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) {

        String userEmail = authentication.getName();
        String token = authHeader.substring(7);
        String userRole = jwtUtil.extractRole(token);

        return ResponseEntity.ok(ticketService.getTicketDetail(id, userEmail, userRole));
    }

    /**
     * Adds a new message to a ticket's timeline (used by both the requester and the admin).
     * Now multipart: accepts an optional text "content" and/or an optional image "attachment",
     * sent together in a single request — mirrors a real chat reply UX.
     *
     * POST /api/tickets/{id}/activities
     */
    @PostMapping(value = "/{id}/activities", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketActivityResponse> addActivity(
            @PathVariable Long id,
            @ModelAttribute TicketActivityRequest request,
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) throws IOException {

        String userEmail = authentication.getName();
        String token = authHeader.substring(7);
        String userRole = jwtUtil.extractRole(token);

        TicketActivityResponse response = ticketService.addActivity(id, request, userEmail, userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates the status of a ticket (OPEN / IN_PROGRESS / CLOSED). ADMIN only.
     *
     * PATCH /api/tickets/{id}/status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody TicketStatusUpdateRequest request,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        String userRole = jwtUtil.extractRole(token);

        return ResponseEntity.ok(ticketService.updateStatus(id, request, userRole));
    }
}