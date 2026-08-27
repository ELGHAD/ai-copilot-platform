package com.alten.chat_service.exception;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(Long ticketId) {
        super("Ticket not found with id: " + ticketId);
    }
}