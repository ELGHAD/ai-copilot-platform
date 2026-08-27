import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSelectModule } from '@angular/material/select';
import { SidebarComponent } from '../../../../shared/components/sidebar/sidebar';
import { DocumentService, DocumentResponse } from '../../../../core/services/document.service';
import { TranslationService } from '../../../../core/services/translation.service';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSelectModule,
    SidebarComponent
  ],
  templateUrl: './documents.html',
  styleUrl: './documents.scss'
})
export class DocumentsComponent implements OnInit {

  private documentService = inject(DocumentService);
  ts = inject(TranslationService);

  documents = signal<DocumentResponse[]>([]);
  isLoading = signal(true);
  error = signal('');
  successMessage = signal('');

  /** Upload modal state */
  showUploadModal = signal(false);
  isUploading = signal(false);

  /** Edit modal state */
  showEditModal = signal(false);
  editingDocument = signal<DocumentResponse | null>(null);

  /** Filter */
  selectedStatus = signal<string>('ALL');

  /** Upload form fields */
  uploadTitle = '';
  uploadDescription = '';
  uploadRoleAccess = 'COMMUN';
  selectedFile: File | null = null;
  fileError = '';

  /** Edit form fields */
  editTitle = '';
  editDescription = '';
  editRoleAccess = '';
  editStatus: 'ACTIVE' | 'OBSOLETE' | 'ARCHIVED' = 'ACTIVE';

  roles = ['COMMUN', 'OPERATIONNEL', 'EXPERT', 'ADMIN'];
  statuses = ['ACTIVE', 'OBSOLETE', 'ARCHIVED'];
  filterOptions = ['ALL', 'ACTIVE', 'OBSOLETE', 'ARCHIVED'];

  ngOnInit(): void {
    this.loadDocuments();
  }

  /**
   * Loads all documents from the backend.
   */
  loadDocuments(): void {
    this.isLoading.set(true);
    this.error.set('');

    this.documentService.getAll().subscribe({
      next: (docs) => {
        this.documents.set(docs);
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set(this.ts.t().common_error);
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Returns documents filtered by selected status.
   */
  get filteredDocuments(): DocumentResponse[] {
    const status = this.selectedStatus();
    if (status === 'ALL') return this.documents();
    return this.documents().filter(d => d.status === status);
  }

  /**
   * Handles file selection from input.
   */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    const allowed = ['application/pdf',
      'application/msword',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];

    if (!allowed.includes(file.type)) {
      this.fileError = 'Seuls les fichiers PDF et Word sont acceptés';
      this.selectedFile = null;
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.fileError = 'La taille maximale est 10MB';
      this.selectedFile = null;
      return;
    }

    this.fileError = '';
    this.selectedFile = file;
  }

  /**
   * Uploads a new document.
   */
  uploadDocument(): void {
    if (!this.selectedFile || !this.uploadTitle) return;

    this.isUploading.set(true);

    this.documentService.upload(
      this.selectedFile,
      this.uploadTitle,
      this.uploadDescription,
      this.uploadRoleAccess
    ).subscribe({
      next: (doc) => {
        this.documents.update(docs => [doc, ...docs]);
        this.showUploadModal.set(false);
        this.resetUploadForm();
        this.isUploading.set(false);
        this.showSuccess(this.ts.t().docs_upload_success);
      },
      error: () => {
        this.isUploading.set(false);
        this.error.set(this.ts.t().common_error);
      }
    });
  }

  /**
   * Opens the edit modal for a document.
   */
  openEditModal(doc: DocumentResponse): void {
    this.editingDocument.set(doc);
    this.editTitle = doc.title;
    this.editDescription = doc.description || '';
    this.editRoleAccess = doc.roleAccess;
    this.editStatus = doc.status;
    this.showEditModal.set(true);
  }

  /**
   * Saves document metadata changes.
   */
  saveEdit(): void {
    const doc = this.editingDocument();
    if (!doc) return;

    this.documentService.update(doc.id, {
      title: this.editTitle,
      description: this.editDescription,
      roleAccess: this.editRoleAccess,
      status: this.editStatus
    }).subscribe({
      next: (updated) => {
        this.documents.update(docs =>
          docs.map(d => d.id === updated.id ? updated : d)
        );
        this.showEditModal.set(false);
        this.showSuccess(this.ts.t().common_success);
      },
      error: () => {
        this.error.set(this.ts.t().common_error);
      }
    });
  }

  /**
   * Archives a document after confirmation.
   */
  deleteDocument(doc: DocumentResponse): void {
    if (!confirm(this.ts.t().docs_delete_confirm)) return;

    this.documentService.delete(doc.id).subscribe({
      next: () => {
        this.documents.update(docs => docs.filter(d => d.id !== doc.id));
        this.showSuccess(this.ts.t().docs_delete_success);
      },
      error: () => {
        this.error.set(this.ts.t().common_error);
      }
    });
  }

  /**
   * Returns CSS class for status badge.
   */
  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'badge-active';
      case 'OBSOLETE': return 'badge-obsolete';
      case 'ARCHIVED': return 'badge-archived';
      default: return '';
    }
  }

  formatSize(bytes: number): string {
    return this.documentService.formatFileSize(bytes);
  }

  private showSuccess(message: string): void {
    this.successMessage.set(message);
    setTimeout(() => this.successMessage.set(''), 3000);
  }

  private resetUploadForm(): void {
    this.uploadTitle = '';
    this.uploadDescription = '';
    this.uploadRoleAccess = 'COMMUN';
    this.selectedFile = null;
    this.fileError = '';
  }
}