package com.alten.chat_service.repository;


import com.alten.chat_service.model.Ticket;
import com.alten.chat_service.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    // Pour la vue "Mes tickets" côté user
    List<Ticket> findByRequesterEmailOrderByCreatedAtDesc(String requesterEmail);

    // Pour la vue admin : tous les tickets, triés du plus récent au plus ancien
    List<Ticket> findAllByOrderByCreatedAtDesc();

    // Filtrage admin par statut
    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    // Pour générer le prochain numéro de ticket (ex: TCK0001, TCK0002...)
    @Query("SELECT COUNT(t) FROM Ticket t")
    long countAllTickets();

}