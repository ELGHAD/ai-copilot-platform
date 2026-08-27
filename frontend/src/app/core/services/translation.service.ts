import { Injectable, signal } from '@angular/core';

export type Language = 'fr' | 'en';

export interface Translations {
  // Navigation
  nav_documents: string;
  nav_users: string;
  nav_dashboard: string;
  nav_chat: string;
  nav_logout: string;
  nav_profile: string;

  // Dashboard
  dashboard_title: string;
  dashboard_subtitle: string;
  dashboard_total_docs: string;
  dashboard_total_users: string;
  dashboard_active_docs: string;
  dashboard_conversations: string;

  // Documents
  docs_title: string;
  docs_upload: string;
  docs_search: string;
  docs_name: string;
  docs_role: string;
  docs_status: string;
  docs_date: string;
  docs_actions: string;
  docs_delete_confirm: string;
  docs_upload_success: string;
  docs_delete_success: string;

  // Users
  users_title: string;
  users_search: string;
  users_name: string;
  users_email: string;
  users_role: string;
  users_status: string;
  users_actions: string;
  users_active: string;
  users_inactive: string;
  users_disable_confirm: string;

  // Chat
  chat_placeholder: string;
  chat_new: string;
  chat_history: string;
  chat_send: string;
  chat_sources: string;
  chat_confidence: string;
  chat_empty: string;
  chat_welcome: string;
  chat_welcome_sub: string;
  chat_mic_tooltip: string;        // ← nouveau
  chat_mic_not_supported: string;  // ← nouveau
  // Common
  common_save: string;
  common_cancel: string;
  common_delete: string;
  common_edit: string;
  common_loading: string;
  common_error: string;
  common_success: string;
  common_active: string;
  common_inactive: string;
  common_search: string;
}

const FR: Translations = {
  nav_documents: 'Documents',
  nav_users: 'Utilisateurs',
  nav_dashboard: 'Tableau de bord',
  nav_chat: 'Chat IA',
  
  nav_logout: 'Déconnexion',
  nav_profile: 'Mon profil',

  dashboard_title: 'Tableau de bord',
  dashboard_subtitle: 'Vue d\'ensemble de la plateforme',
  dashboard_total_docs: 'Documents total',
  dashboard_total_users: 'Utilisateurs',
  dashboard_active_docs: 'Documents actifs',
  dashboard_conversations: 'Conversations',

  docs_title: 'Gestion des documents',
  docs_upload: 'Importer un document',
  docs_search: 'Rechercher un document...',
  docs_name: 'Nom du document',
  docs_role: 'Accès par rôle',
  docs_status: 'Statut',
  docs_date: 'Date d\'import',
  docs_actions: 'Actions',
  docs_delete_confirm: 'Voulez-vous vraiment archiver ce document ?',
  docs_upload_success: 'Document importé avec succès',
  docs_delete_success: 'Document archivé avec succès',

  users_title: 'Gestion des utilisateurs',
  users_search: 'Rechercher un utilisateur...',
  users_name: 'Nom complet',
  users_email: 'Email',
  users_role: 'Rôle',
  users_status: 'Statut',
  users_actions: 'Actions',
  users_active: 'Actif',
  users_inactive: 'Inactif',
  users_disable_confirm: 'Voulez-vous vraiment désactiver cet utilisateur ?',

  chat_placeholder: 'Posez votre question...',
  chat_new: 'Nouvelle conversation',
  chat_history: 'Historique',
  chat_send: 'Envoyer',
  chat_sources: 'Sources',
  chat_confidence: 'Fiabilité',
  chat_empty: 'Aucune conversation',
  chat_welcome: 'Bonjour',
  chat_welcome_sub: 'Comment puis-je vous aider aujourd\'hui ?',
  chat_mic_tooltip: 'Parler pour poser votre question',
  chat_mic_not_supported: 'La reconnaissance vocale n\'est pas supportée par votre navigateur',
 
  common_save: 'Enregistrer',
  common_cancel: 'Annuler',
  common_delete: 'Supprimer',
  common_edit: 'Modifier',
  common_loading: 'Chargement...',
  common_error: 'Une erreur est survenue',
  common_success: 'Opération réussie',
  common_active: 'Actif',
  common_inactive: 'Inactif',
  common_search: 'Rechercher...',
};

const EN: Translations = {
  nav_documents: 'Documents',
  nav_users: 'Users',
  nav_dashboard: 'Dashboard',
  nav_chat: 'AI Chat',
  nav_logout: 'Logout',
  nav_profile: 'My profile',

  dashboard_title: 'Dashboard',
  dashboard_subtitle: 'Platform overview',
  dashboard_total_docs: 'Total documents',
  dashboard_total_users: 'Users',
  dashboard_active_docs: 'Active documents',
  dashboard_conversations: 'Conversations',

  docs_title: 'Document management',
  docs_upload: 'Upload document',
  docs_search: 'Search document...',
  docs_name: 'Document name',
  docs_role: 'Role access',
  docs_status: 'Status',
  docs_date: 'Upload date',
  docs_actions: 'Actions',
  docs_delete_confirm: 'Are you sure you want to archive this document?',
  docs_upload_success: 'Document uploaded successfully',
  docs_delete_success: 'Document archived successfully',

  users_title: 'User management',
  users_search: 'Search user...',
  users_name: 'Full name',
  users_email: 'Email',
  users_role: 'Role',
  users_status: 'Status',
  users_actions: 'Actions',
  users_active: 'Active',
  users_inactive: 'Inactive',
  users_disable_confirm: 'Are you sure you want to disable this user?',

  chat_placeholder: 'Ask your question...',
  chat_new: 'New conversation',
  chat_history: 'History',
  chat_send: 'Send',
  chat_sources: 'Sources',
  chat_confidence: 'Confidence',
  chat_empty: 'No conversations',
  chat_welcome: 'Hello',
 chat_welcome_sub: 'How can I help you today?',
  chat_mic_tooltip: 'Speak to ask your question',
  chat_mic_not_supported: 'Speech recognition is not supported by your browser',

  common_save: 'Save',
  common_cancel: 'Cancel',
  common_delete: 'Delete',
  common_edit: 'Edit',
  common_loading: 'Loading...',
  common_error: 'An error occurred',
  common_success: 'Operation successful',
  common_active: 'Active',
  common_inactive: 'Inactive',
  common_search: 'Search...',
};

/**
 * Lightweight translation service using Angular signals.
 * Supports FR and EN without external i18n libraries.
 * Language preference is persisted in localStorage.
 */
@Injectable({
  providedIn: 'root'
})
export class TranslationService {

  private readonly LANG_KEY = 'alten_language';

  /** Current language as a signal — components react automatically */
  currentLang = signal<Language>(
    (localStorage.getItem(this.LANG_KEY) as Language) || 'fr'
  );

  /** Current translations as a signal */
  t = signal<Translations>(
    this.currentLang() === 'fr' ? FR : EN
  );

  /**
   * Switches the application language.
   * Updates both signals and persists the choice in localStorage.
   *
   * @param lang the language to switch to
   */
  setLanguage(lang: Language): void {
    this.currentLang.set(lang);
    this.t.set(lang === 'fr' ? FR : EN);
    localStorage.setItem(this.LANG_KEY, lang);
  }

  /**
   * Toggles between FR and EN.
   */
  toggleLanguage(): void {
    this.setLanguage(this.currentLang() === 'fr' ? 'en' : 'fr');
  }
}