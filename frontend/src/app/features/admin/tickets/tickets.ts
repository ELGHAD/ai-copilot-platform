import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SidebarComponent } from '../../../shared/components/sidebar/sidebar';
import { TicketService } from '../../../core/services/ticket.service';
import { TranslationService } from '../../../core/services/translation.service';
import {
  Ticket,
  TicketSummary,
  TicketStatus,
  TicketType,
  SenderRole,
  TicketCategorySchemas,
  TICKET_CATEGORY_META
} from '../../../core/models/ticket.model';

const MAX_ATTACHMENT_SIZE = 5 * 1024 * 1024;
const ALLOWED_ATTACHMENT_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/gif', 'image/webp'];

@Component({
  selector: 'app-tickets',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    SidebarComponent
  ],
  templateUrl: './tickets.html',
  styleUrl: './tickets.scss'
})
export class TicketsComponent implements OnInit {

  private ticketService = inject(TicketService);
  ts = inject(TranslationService);

  TicketStatus = TicketStatus;
  TicketType = TicketType;
  SenderRole = SenderRole;
  categoryMeta = TICKET_CATEGORY_META;

  tickets = signal<TicketSummary[]>([]);
  isLoading = signal(true);
  error = signal('');
  successMessage = signal('');

  // Schemas fetched once, used to resolve formData field labels for any category
  categorySchemas = signal<TicketCategorySchemas>({});

  selectedStatus = signal<string>('ALL');
  filterOptions = ['ALL', ...Object.values(TicketStatus)];

  sortColumn = signal<'ticketNumber' | 'requesterName' | 'createdAt'>('createdAt');
  sortDirection = signal<'asc' | 'desc'>('desc');

  showDetailModal = signal(false);
  selectedTicket = signal<Ticket | null>(null);
  isLoadingDetail = signal(false);
  isReplying = signal(false);
  replyContent = '';

  selectedFile = signal<File | null>(null);
  filePreviewUrl = signal<string | null>(null);
  attachmentError = signal('');

  ngOnInit(): void {
    this.loadTickets();
    this.loadCategorySchemas();
  }

  /**
   * Loads the field schema for every category once, so we can resolve
   * human-readable labels for formData keys in the detail modal.
   */
  loadCategorySchemas(): void {
    this.ticketService.getCategories().subscribe({
      next: (schemas) => this.categorySchemas.set(schemas),
      error: () => {} // non-blocking: falls back to raw keys if this fails
    });
  }

  loadTickets(): void {
    this.isLoading.set(true);
    this.error.set('');

    this.ticketService.getTickets().subscribe({
      next: (tickets) => {
        this.tickets.set(tickets);
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set(this.ts.t().common_error ?? 'Une erreur est survenue');
        this.isLoading.set(false);
      }
    });
  }

  sortBy(column: 'ticketNumber' | 'requesterName' | 'createdAt'): void {
    if (this.sortColumn() === column) {
      this.sortDirection.update(d => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortColumn.set(column);
      this.sortDirection.set('asc');
    }
  }

  get filteredTickets(): TicketSummary[] {
    const status = this.selectedStatus();
    const list = status === 'ALL' ? this.tickets() : this.tickets().filter(t => t.status === status);
    const col = this.sortColumn();
    const dir = this.sortDirection() === 'asc' ? 1 : -1;

    return [...list].sort((a, b) => {
      const av = a[col];
      const bv = b[col];
      if (av < bv) return -1 * dir;
      if (av > bv) return 1 * dir;
      return 0;
    });
  }

  /**
   * Returns the display label for a ticket category (used in the table's Type column).
   */
  getCategoryLabel(type: TicketType): string {
    if (type === TicketType.FREE_TEXT) return 'Message libre';
    return this.categoryMeta[type]?.label ?? type;
  }

  /**
   * Turns a ticket's formData into an ordered array of {label, value} pairs,
   * resolving each key's label from the category's schema. Falls back to the
   * raw key if the schema hasn't loaded yet or the field is unknown.
   * Empty/undefined values are skipped.
   */
  formDataEntries(ticket: Ticket): { label: string; value: string }[] {
    if (!ticket.formData) return [];
    const schema = this.categorySchemas()[ticket.ticketType] ?? [];

    return Object.entries(ticket.formData)
      .filter(([, value]) => value !== null && value !== undefined && value !== '')
      .map(([key, value]) => {
        const field = schema.find(f => f.key === key);
        const label = field?.label ?? key;
        const displayValue = typeof value === 'boolean' ? (value ? 'Oui' : 'Non') : String(value);
        return { label, value: displayValue };
      });
  }

  openDetail(ticket: TicketSummary): void {
    this.showDetailModal.set(true);
    this.isLoadingDetail.set(true);
    this.selectedTicket.set(null);
    this.replyContent = '';
    this.clearSelectedFile();

    this.ticketService.getTicketDetail(ticket.id).subscribe({
      next: (full) => {
        this.selectedTicket.set(full);
        this.isLoadingDetail.set(false);
      },
      error: () => {
        this.error.set(this.ts.t().common_error ?? 'Une erreur est survenue');
        this.isLoadingDetail.set(false);
        this.showDetailModal.set(false);
      }
    });
  }

  closeDetail(): void {
    this.showDetailModal.set(false);
    this.selectedTicket.set(null);
    this.clearSelectedFile();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    this.attachmentError.set('');

    if (!ALLOWED_ATTACHMENT_TYPES.includes(file.type)) {
      this.attachmentError.set('Format non supporté. Utilisez une image (PNG, JPEG, GIF, WEBP).');
      return;
    }
    if (file.size > MAX_ATTACHMENT_SIZE) {
      this.attachmentError.set('Image trop volumineuse (5 Mo max).');
      return;
    }

    this.clearSelectedFile();
    this.selectedFile.set(file);
    this.filePreviewUrl.set(URL.createObjectURL(file));
  }

  clearSelectedFile(): void {
    const current = this.filePreviewUrl();
    if (current) URL.revokeObjectURL(current);
    this.selectedFile.set(null);
    this.filePreviewUrl.set(null);
    this.attachmentError.set('');
  }

  getAttachmentUrl(relativeUrl: string): string {
    return this.ticketService.getAttachmentUrl(relativeUrl);
  }

  sendReply(): void {
    const ticket = this.selectedTicket();
    const file = this.selectedFile();
    const hasText = !!this.replyContent.trim();
    if (!ticket || (!hasText && !file)) return;

    this.isReplying.set(true);

    this.ticketService.addActivity(ticket.id, this.replyContent, file ?? undefined).subscribe({
      next: (activity) => {
        this.selectedTicket.update(t => t ? {
          ...t,
          activities: [...t.activities, activity],
          status: t.status === TicketStatus.OPEN ? TicketStatus.IN_PROGRESS : t.status
        } : t);
        this.replyContent = '';
        this.clearSelectedFile();
        this.isReplying.set(false);
        this.loadTickets();
      },
      error: () => {
        this.isReplying.set(false);
        this.error.set(this.ts.t().common_error ?? 'Une erreur est survenue');
      }
    });
  }

  changeStatus(status: TicketStatus): void {
    const ticket = this.selectedTicket();
    if (!ticket) return;

    this.ticketService.updateStatus(ticket.id, { status }).subscribe({
      next: (updated) => {
        this.selectedTicket.set(updated);
        this.loadTickets();
        this.showSuccess(this.ts.t().common_success ?? 'Statut mis à jour');
      },
      error: () => {
        this.error.set(this.ts.t().common_error ?? 'Une erreur est survenue');
      }
    });
  }

  getStatusColor(status: TicketStatus): string {
    return this.ticketService.getStatusColor(status);
  }

  getInitials(fullName: string): string {
    if (!fullName) return '?';
    return fullName.split(' ').map(n => n[0]).slice(0, 2).join('').toUpperCase();
  }

  private showSuccess(message: string): void {
    this.successMessage.set(message);
    setTimeout(() => this.successMessage.set(''), 3000);
  }
}