import { Component, inject, OnInit, signal, ElementRef, ViewChild, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SidebarComponent } from '../../../shared/components/sidebar/sidebar';
import { ChatService, ConversationSummary, MessageResponse } from '../../../core/services/chat.service';
import { TranslationService } from '../../../core/services/translation.service';
import { AuthService } from '../../../core/services/auth';
import { TextFieldModule } from '@angular/cdk/text-field';
import { TicketService } from '../../../core/services/ticket.service';
import {
  Ticket,
  TicketSummary,
  TicketStatus,
  TicketType,
  SenderRole,
  TicketRequest,
  TicketCategorySchemas,
  TicketCategoryField,
  TICKET_CATEGORY_META
} from '../../../core/models/ticket.model';
import { SpeechRecognitionService } from '../../../core/services/speech-recognition.service';

const MAX_ATTACHMENT_SIZE = 5 * 1024 * 1024; // 5 MB, keep in sync with backend FileStorageService
const ALLOWED_ATTACHMENT_TYPES = ['image/png', 'image/jpeg', 'image/jpg', 'image/gif', 'image/webp'];

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    TextFieldModule,
  ],
  templateUrl: './chat.html',
  styleUrl: './chat.scss'
})
export class ChatComponent implements OnInit, AfterViewChecked {
  private speechService = inject(SpeechRecognitionService);
isListening = this.speechService.isListening;
isSpeechSupported = this.speechService.isSupported;
private questionBeforeListening = '';
  
  
  private ticketService = inject(TicketService);

// ─── Ticket creation modal state ───────────────────────────────────────────
TicketType = TicketType;
TicketStatus = TicketStatus;
SenderRole = SenderRole;
categoryMeta = TICKET_CATEGORY_META;

showTicketModal = signal(false);
ticketStep = signal<'choice' | 'categories' | 'dynamicForm' | 'freetext'>('choice');
selectedMessageForTicket = signal<MessageResponse | null>(null);

isCreatingTicket = signal(false);
ticketCreated = signal(false);
createdTicketNumber = signal('');
ticketError = signal('');

ticketSubject = '';
ticketFreeText = '';

// ─── Category list + dynamic form state ────────────────────────────────────
ticketCategories = signal<TicketCategorySchemas>({});
isLoadingCategories = signal(false);
selectedCategory = signal<TicketType | null>(null);
selectedCategoryFields = signal<TicketCategoryField[]>([]);
dynamicFormData: Record<string, any> = {};

categoryKeys(): string[] {
  return Object.keys(this.ticketCategories());
}

  // ─── My tickets modal state ────────────────────────────────────────────────
  showMyTicketsModal = signal(false);
  myTicketsView = signal<'list' | 'detail'>('list');
  myTickets = signal<TicketSummary[]>([]);
  isLoadingMyTickets = signal(false);
  selectedMyTicket = signal<Ticket | null>(null);
  isLoadingMyTicketDetail = signal(false);
  myTicketReplyContent = '';
  isSendingMyTicketReply = signal(false);

  // ─── My tickets reply attachment state ─────────────────────────────────────
  myTicketSelectedFile = signal<File | null>(null);
  myTicketFilePreviewUrl = signal<string | null>(null);
  myTicketAttachmentError = signal('');

  @ViewChild('messagesContainer') messagesContainer!: ElementRef;

  private chatService = inject(ChatService);
  ts = inject(TranslationService);
  authService = inject(AuthService);

  conversations = signal<ConversationSummary[]>([]);
  messages = signal<MessageResponse[]>([]);
  activeConversationId = signal<number | null>(null);

  question = '';
  isLoading = signal(false);
  isSending = signal(false);
  error = signal('');

  /** Controls whether sources panel is expanded for a message */
  expandedSources = new Set<number>();

  user = this.authService.getCurrentUser();
  private shouldScrollToBottom = false;

  ngOnInit(): void {
    this.loadConversations();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  /**
   * Loads all conversations for the current user.
   */
  loadConversations(): void {
    this.chatService.getConversations().subscribe({
      next: (convs) => this.conversations.set(convs),
      error: () => this.error.set(this.ts.t().common_error)
    });
  }

  /**
   * Selects a conversation and loads its messages.
   */
  selectConversation(id: number): void {
    this.activeConversationId.set(id);
    this.isLoading.set(true);
    this.error.set('');

    this.chatService.getMessages(id).subscribe({
      next: (msgs) => {
        this.messages.set(msgs);
        this.isLoading.set(false);
        this.shouldScrollToBottom = true;
      },
      error: () => {
        this.error.set(this.ts.t().common_error);
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Starts a new conversation.
   */
  newConversation(): void {
    this.activeConversationId.set(null);
    this.messages.set([]);
    this.question = '';
    this.error.set('');
  }

  /**
   * Sends a question to the RAG pipeline.
   */
  sendMessage(): void {
    if (!this.question.trim() || this.isSending()) return;

    const questionText = this.question.trim();
    this.question = '';
    this.isSending.set(true);
    this.error.set('');

    // Add user message immediately for better UX
    const userMessage: MessageResponse = {
      id: Date.now(),
      role: 'USER',
      content: questionText,
      sources: [],
      confidenceScore: 0,
      createdAt: new Date().toISOString()
    };
    this.messages.update(msgs => [...msgs, userMessage]);
    this.shouldScrollToBottom = true;

    this.chatService.ask({
      question: questionText,
      conversationId: this.activeConversationId() ?? undefined
    }).subscribe({
      next: (response) => {
        this.activeConversationId.set(response.conversationId);

        // Add assistant response
        const assistantMessage: MessageResponse = {
          id: response.messageId,
          role: 'ASSISTANT',
          content: response.answer,
          sources: response.sources || [],
          confidenceScore: response.confidenceScore,
          createdAt: response.createdAt
        };
        this.messages.update(msgs => [...msgs, assistantMessage]);
        this.isSending.set(false);
        this.shouldScrollToBottom = true;

        // Refresh conversation list
        this.loadConversations();
      },
      error: () => {
        this.isSending.set(false);
        this.error.set(this.ts.t().common_error);
      }
    });
  }

  /**
 * Démarre/arrête la saisie vocale et injecte le texte transcrit dans l'input.
 */
toggleMic(): void {
  if (this.isListening()) {
    this.speechService.stop();
    return;
  }

  if (!this.isSpeechSupported()) {
    this.error.set(this.ts.t().chat_mic_not_supported);
    return;
  }

  // Conserve le texte déjà tapé pour ne pas l'écraser
  this.questionBeforeListening = this.question.trim() ? this.question.trim() + ' ' : '';
  const lang = this.ts.currentLang() === 'fr' ? 'fr-FR' : 'en-US';

  this.speechService.start(
    lang,
    (transcript, isFinal) => {
      this.question = this.questionBeforeListening + transcript;
      if (isFinal) {
        this.questionBeforeListening = this.question.trim() + ' ';
      }
    },
    () => this.error.set(this.ts.t().chat_mic_not_supported)
  );
}
  /**
   * Sends message on Enter key (Shift+Enter for new line).
   */
  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  /**
   * Deletes a conversation after confirmation.
   */
  deleteConversation(event: Event, id: number): void {
    event.stopPropagation();
    if (!confirm('Supprimer cette conversation ?')) return;

    this.chatService.deleteConversation(id).subscribe({
      next: () => {
        this.conversations.update(convs => convs.filter(c => c.id !== id));
        if (this.activeConversationId() === id) {
          this.newConversation();
        }
      }
    });
  }

  /**
   * Toggles sources panel for a message.
   */
  toggleSources(messageId: number): void {
    if (this.expandedSources.has(messageId)) {
      this.expandedSources.delete(messageId);
    } else {
      this.expandedSources.add(messageId);
    }
  }

  /**
   * Opens the ticket creation modal for a given assistant message.
   */
openTicketModal(message: MessageResponse): void {
  this.selectedMessageForTicket.set(message);
  this.ticketStep.set('choice');
  this.ticketSubject = '';
  this.ticketFreeText = '';
  this.selectedCategory.set(null);
  this.selectedCategoryFields.set([]);
  this.dynamicFormData = {};
  this.ticketCreated.set(false);
  this.ticketError.set('');
  this.showTicketModal.set(true);
}

  /**
   * Closes the ticket modal and resets its state.
   */
  closeTicketModal(): void {
    this.showTicketModal.set(false);
  }

  /**
   * User picks between "Formulaire" and "Message libre".
   */
 chooseTicketType(step: 'form' | 'freetext'): void {
  if (step === 'freetext') {
    this.ticketStep.set('freetext');
    return;
  }

  this.ticketStep.set('categories');
  this.loadTicketCategories();
}
loadTicketCategories(): void {
  if (Object.keys(this.ticketCategories()).length > 0) {
    return;
  }

  this.isLoadingCategories.set(true);

  this.ticketService.getCategories().subscribe({
    next: (schemas) => {
      this.ticketCategories.set(schemas);
      this.isLoadingCategories.set(false);
    },
    error: () => {
      this.isLoadingCategories.set(false);
      this.ticketError.set(this.ts.t().common_error);
    }
  });
}

selectCategory(categoryKey: string): void {
  const type = categoryKey as TicketType;
  const fields = this.ticketCategories()[categoryKey] ?? [];

  this.selectedCategory.set(type);
  this.selectedCategoryFields.set(fields);

  this.dynamicFormData = {};

  for (const field of fields) {
    if (field.type === 'checkbox') {
      this.dynamicFormData[field.key] = false;
    }
  }

  this.ticketStep.set('dynamicForm');
}

backToTicketChoice(): void {
  this.ticketStep.set('choice');
}

backToCategories(): void {
  this.selectedCategory.set(null);
  this.ticketStep.set('categories');
}

isDynamicFormValid(): boolean {
  return this.selectedCategoryFields().every(field => {
    if (!field.required) {
      return true;
    }

    const value = this.dynamicFormData[field.key];

    return (
      value !== undefined &&
      value !== null &&
      String(value).trim() !== ''
    );
  });
}

submitDynamicForm(): void {
  const type = this.selectedCategory();

  if (
    !type ||
    !this.ticketSubject.trim() ||
    !this.isDynamicFormValid()
  ) {
    return;
  }

  this.createTicket({
    ticketType: type,
    subject: this.ticketSubject.trim(),
    requesterName: this.user?.fullName ?? '',
    formData: this.dynamicFormData
  });
}

  /**
   * Goes back to the choice screen from either sub-form.
   */


  /**
   * Submits the free-text ticket.
   */
  submitFreeTextTicket(): void {
    if (!this.ticketSubject.trim() || !this.ticketFreeText.trim()) return;
    this.createTicket({
      ticketType: TicketType.FREE_TEXT,
      subject: this.ticketSubject.trim(),
      freeTextContent: this.ticketFreeText.trim(),
      requesterName: this.user?.fullName ?? ''
    });
  }

  /**
   * Submits the software installation form ticket.
   */
 

  /**
   * Shared submission logic: attaches the triggering conversation/message,
   * calls the backend, and shows a confirmation screen on success.
   */
  private createTicket(partial: Partial<TicketRequest>): void {
    this.isCreatingTicket.set(true);
    this.ticketError.set('');

    const message = this.selectedMessageForTicket();

    const request: TicketRequest = {
      ...partial,
      conversationId: this.activeConversationId() ?? undefined,
      messageId: message?.id
    } as TicketRequest;

    this.ticketService.createTicket(request).subscribe({
      next: (ticket) => {
        this.isCreatingTicket.set(false);
        this.ticketCreated.set(true);
        this.createdTicketNumber.set(ticket.ticketNumber);
      },
      error: () => {
        this.isCreatingTicket.set(false);
        this.ticketError.set(this.ts.t().common_error);
      }
    });
  }

  // ─── My tickets ─────────────────────────────────────────────────────────────

  /**
   * Opens the "My tickets" modal and loads the user's tickets.
   * Reuses getTickets(): the backend already scopes results to the
   * authenticated user for non-admin roles.
   */
  openMyTickets(): void {
    this.showMyTicketsModal.set(true);
    this.myTicketsView.set('list');
    this.loadMyTickets();
  }

  loadMyTickets(): void {
    this.isLoadingMyTickets.set(true);
    this.ticketService.getTickets().subscribe({
      next: (tickets) => {
        this.myTickets.set(tickets);
        this.isLoadingMyTickets.set(false);
      },
      error: () => {
        this.isLoadingMyTickets.set(false);
      }
    });
  }

  /**
   * Opens the detail/timeline view for one of the user's own tickets.
   */
  openMyTicketDetail(ticket: TicketSummary): void {
    this.myTicketsView.set('detail');
    this.isLoadingMyTicketDetail.set(true);
    this.selectedMyTicket.set(null);
    this.myTicketReplyContent = '';
    this.clearMyTicketAttachment();

    this.ticketService.getTicketDetail(ticket.id).subscribe({
      next: (full) => {
        this.selectedMyTicket.set(full);
        this.isLoadingMyTicketDetail.set(false);
      },
      error: () => {
        this.isLoadingMyTicketDetail.set(false);
        this.myTicketsView.set('list');
      }
    });
  }

  backToMyTicketsList(): void {
    this.myTicketsView.set('list');
    this.selectedMyTicket.set(null);
    this.clearMyTicketAttachment();
  }

  closeMyTicketsModal(): void {
    this.showMyTicketsModal.set(false);
    this.clearMyTicketAttachment();
  }

  /**
   * Triggered by the hidden <input type="file"> when the user picks an image
   * to attach to their reply (screenshot, error capture, etc.).
   */
  onMyTicketFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file later
    if (!file) return;

    this.myTicketAttachmentError.set('');

    if (!ALLOWED_ATTACHMENT_TYPES.includes(file.type)) {
      this.myTicketAttachmentError.set('Format non supporté. Utilisez une image (PNG, JPEG, GIF, WEBP).');
      return;
    }
    if (file.size > MAX_ATTACHMENT_SIZE) {
      this.myTicketAttachmentError.set('Image trop volumineuse (5 Mo max).');
      return;
    }

    this.clearMyTicketAttachment();
    this.myTicketSelectedFile.set(file);
    this.myTicketFilePreviewUrl.set(URL.createObjectURL(file));
  }

  /**
   * Removes the currently staged attachment (before sending).
   */
  clearMyTicketAttachment(): void {
    const current = this.myTicketFilePreviewUrl();
    if (current) {
      URL.revokeObjectURL(current);
    }
    this.myTicketSelectedFile.set(null);
    this.myTicketFilePreviewUrl.set(null);
    this.myTicketAttachmentError.set('');
  }

  /**
   * Resolves a relative attachment URL for display in the timeline.
   */
  getAttachmentUrl(relativeUrl: string): string {
    return this.ticketService.getAttachmentUrl(relativeUrl);
  }

  /**
   * Sends the user's reply to the admin on their own ticket (text and/or image).
   */
  sendMyTicketReply(): void {
    const ticket = this.selectedMyTicket();
    const file = this.myTicketSelectedFile();
    const hasText = !!this.myTicketReplyContent.trim();
    if (!ticket || (!hasText && !file)) return;

    this.isSendingMyTicketReply.set(true);

    this.ticketService.addActivity(ticket.id, this.myTicketReplyContent, file ?? undefined).subscribe({
      next: (activity) => {
        this.selectedMyTicket.update(t => t ? { ...t, activities: [...t.activities, activity] } : t);
        this.myTicketReplyContent = '';
        this.clearMyTicketAttachment();
        this.isSendingMyTicketReply.set(false);
      },
      error: () => {
        this.isSendingMyTicketReply.set(false);
      }
    });
  }

  getStatusColor(status: TicketStatus): string {
    return this.ticketService.getStatusColor(status);
  }

  /**
   * Returns initials from a full name or email, for timeline avatars.
   */
  getInitials(nameOrEmail: string): string {
    if (!nameOrEmail) return '?';
    if (nameOrEmail.includes('@')) return nameOrEmail[0].toUpperCase();
    return nameOrEmail
      .split(' ')
      .map(n => n[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  /**
   * Returns confidence score as percentage with color.
   */
  getConfidenceColor(score: number): string {
    if (score >= 0.8) return '#2e7d32';
    if (score >= 0.5) return '#f57c00';
    return '#c62828';
  }

  /**
   * Returns initials from full name.
   */
  get userInitials(): string {
    if (!this.user?.fullName) return '?';
    return this.user.fullName
      .split(' ')
      .map(n => n[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  private scrollToBottom(): void {
    try {
      const el = this.messagesContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    } catch {}
  }
}