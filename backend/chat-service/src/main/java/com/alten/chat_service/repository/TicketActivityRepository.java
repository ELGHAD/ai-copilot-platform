package com.alten.chat_service.repository;


import com.alten.chat_service.model.TicketActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketActivityRepository extends JpaRepository<TicketActivity, Long> {

    // Timeline complète d'un ticket, dans l'ordre chronologique (comme le screenshot 1)
    List<TicketActivity> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

}