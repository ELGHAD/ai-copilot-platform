import { Role } from './auth.model';

export enum TicketType {
  FREE_TEXT = 'FREE_TEXT',
  FORM_SOFTWARE_INSTALL = 'FORM_SOFTWARE_INSTALL',
  FORM_ACCESS_REQUEST = 'FORM_ACCESS_REQUEST',
  FORM_HARDWARE_ISSUE = 'FORM_HARDWARE_ISSUE',
  FORM_NETWORK_VPN = 'FORM_NETWORK_VPN',
  FORM_USER_ACCOUNT = 'FORM_USER_ACCOUNT',
  FORM_OTHER = 'FORM_OTHER'
}

export enum TicketStatus {
  OPEN = 'OPEN',
  IN_PROGRESS = 'IN_PROGRESS',
  CLOSED = 'CLOSED'
}

export enum SenderRole {
  USER = 'USER',
  ADMIN = 'ADMIN',
  SYSTEM = 'SYSTEM'
}

/**
 * One field definition coming from GET /api/tickets/categories,
 * mirrors backend's TicketCategorySchemas.FieldRule.
 */
export interface TicketCategoryField {
  key: string;
  label: string;
  required: boolean;
  type: 'text' | 'textarea' | 'checkbox';
  placeholder?: string;
}

/**
 * Response of GET /api/tickets/categories: map of TicketType -> its fields.
 * FREE_TEXT is not included (handled separately via freeTextContent).
 */
export type TicketCategorySchemas = Record<string, TicketCategoryField[]>;

/**
 * Frontend-only metadata (labels/icons) for the category picker,
 * since the backend enum values aren't meant for display.
 */
export const TICKET_CATEGORY_META: Record<string, { label: string; description: string; icon: string }> = {
  FORM_SOFTWARE_INSTALL: { label: 'Installation logicielle', description: "Demander l'installation d'un logiciel", icon: 'download' },
  FORM_ACCESS_REQUEST:   { label: 'Accès / permissions', description: 'Demander un accès à une application', icon: 'lock_open' },
  FORM_HARDWARE_ISSUE:   { label: 'Problème matériel', description: 'PC, écran, périphérique...', icon: 'devices' },
  FORM_NETWORK_VPN:      { label: 'Réseau / VPN', description: 'Problème de connexion, VPN, WiFi...', icon: 'wifi' },
  FORM_USER_ACCOUNT:     { label: 'Compte utilisateur', description: 'Création, modification, suppression', icon: 'person' },
  FORM_OTHER:            { label: 'Autre demande', description: "Ce qui ne correspond à aucune catégorie", icon: 'more_horiz' },
};

/**
 * Payload sent to POST /api/tickets
 */
export interface TicketRequest {
  conversationId?: number;
  messageId?: number;
  ticketType: TicketType;
  subject: string;
  requesterName: string;

  // FREE_TEXT
  freeTextContent?: string;

  // Any structured form category
  formData?: Record<string, any>;
}

/**
 * Payload sent to PATCH /api/tickets/{id}/status
 */
export interface TicketStatusUpdateRequest {
  status: TicketStatus;
}

export interface TicketActivity {
  id: number;
  senderEmail: string;
  senderRole: SenderRole;
  content?: string;
  attachmentUrl?: string;
  createdAt: string;
}

/**
 * Full ticket detail, including its timeline (matches TicketResponse)
 */
export interface Ticket {
  id: number;
  ticketNumber: string;
  conversationId?: number;
  messageId?: number;
  requesterEmail: string;
  requesterName: string;
  requesterRole: Role;
  ticketType: TicketType;
  status: TicketStatus;
  subject: string;

  freeTextContent?: string;
  formData?: Record<string, any>;

  assignedAdminEmail?: string;
  createdAt: string;
  updatedAt: string;
  closedAt?: string;

  activities: TicketActivity[];
}

export interface TicketSummary {
  id: number;
  ticketNumber: string;
  requesterName: string;
  requesterEmail: string;
  ticketType: TicketType;
  status: TicketStatus;
  subject: string;
  createdAt: string;
  updatedAt: string;
}