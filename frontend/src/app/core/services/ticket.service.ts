import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Ticket,
  TicketSummary,
  TicketRequest,
  TicketActivity,
  TicketStatusUpdateRequest,
  TicketStatus,
  TicketCategorySchemas
} from '../models/ticket.model';

/**
 * Service that communicates with chat-service's ticket endpoints.
 * Handles ticket creation, listing, timeline replies (text and/or image) and status updates.
 * The Authorization header is added automatically by authInterceptor.
 */
@Injectable({
  providedIn: 'root'
})
export class TicketService {

  private http = inject(HttpClient);
  private readonly BASE_URL = 'http://localhost:8084/api/tickets';

  // Root of chat-service, used to resolve relative attachment URLs returned by the backend
  // (e.g. "/uploads/tickets/12/abc.png" -> "http://localhost:8084/uploads/tickets/12/abc.png")
  readonly FILES_BASE_URL = 'http://localhost:8084';

  /**
   * Creates a new ticket (form-based or free text).
   *
   * @param request ticket payload
   * @returns Observable of the created Ticket (with its seeded timeline)
   */
  createTicket(request: TicketRequest): Observable<Ticket> {
    return this.http.post<Ticket>(this.BASE_URL, request);
  }

  /**
   * Fetches tickets visible to the current user.
   * ADMIN sees all tickets; EXPERT/OPERATIONNEL see only their own.
   *
   * @returns Observable of TicketSummary array
   */
  getTickets(): Observable<TicketSummary[]> {
    return this.http.get<TicketSummary[]>(this.BASE_URL);
  }

  /**
   * Fetches the full detail of a ticket, including its timeline.
   *
   * @param id ticket ID
   * @returns Observable of Ticket
   */
  getTicketDetail(id: number): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.BASE_URL}/${id}`);
  }

  /**
   * Fetches the field schema for every structured ticket category,
   * used to render the category picker and the dynamic form.
   */
  getCategories(): Observable<TicketCategorySchemas> {
    return this.http.get<TicketCategorySchemas>(`${this.BASE_URL}/categories`);
  }

  /**
   * Adds a reply to a ticket's timeline (used by both user and admin).
   * Sent as multipart/form-data so text and an optional image can travel together
   * in a single request, matching a real chat reply UX.
   *
   * At least one of content/attachment must be provided (enforced server-side too).
   *
   * @param id ticket ID
   * @param content optional text content
   * @param attachment optional image file (screenshot, etc.)
   * @returns Observable of the created TicketActivity
   */
  addActivity(
    id: number,
    content?: string,
    attachment?: File
  ): Observable<TicketActivity> {
    const formData = new FormData();

    if (content && content.trim()) {
      formData.append('content', content.trim());
    }

    if (attachment) {
      formData.append('attachment', attachment, attachment.name);
    }

    // Do NOT set Content-Type manually — the browser sets the multipart boundary automatically.
    return this.http.post<TicketActivity>(
      `${this.BASE_URL}/${id}/activities`,
      formData
    );
  }

  /**
   * Updates the status of a ticket. ADMIN only (enforced server-side).
   *
   * @param id ticket ID
   * @param request status payload
   * @returns Observable of updated Ticket
   */
  updateStatus(
    id: number,
    request: TicketStatusUpdateRequest
  ): Observable<Ticket> {
    return this.http.patch<Ticket>(
      `${this.BASE_URL}/${id}/status`,
      request
    );
  }

  /**
   * Resolves a relative attachment URL (as stored in TicketActivity.attachmentUrl)
   * into an absolute URL usable in an ....
   */
  getAttachmentUrl(relativeUrl: string): string {
    return `${this.FILES_BASE_URL}${relativeUrl}`;
  }

  /**
   * Returns a CSS color for each ticket status.
   */
  getStatusColor(status: TicketStatus): string {
    switch (status) {
      case TicketStatus.OPEN:
        return '#e94560';

      case TicketStatus.IN_PROGRESS:
        return '#f57c00';

      case TicketStatus.CLOSED:
        return '#2e7d32';

      default:
        return '#888';
    }
  }
}