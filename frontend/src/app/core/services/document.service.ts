import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DocumentResponse {
  id: number;
  title: string;
  description: string;
  originalFileName: string;
  contentType: string;
  fileSize: number;
  roleAccess: string;
  status: 'ACTIVE' | 'OBSOLETE' | 'ARCHIVED';
  uploadedBy: string;
  version: number;
  indexed: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentUpdateRequest {
  title: string;
  description: string;
  roleAccess: string;
  status: 'ACTIVE' | 'OBSOLETE' | 'ARCHIVED';
}

/**
 * Service that communicates with document-service backend.
 * Handles upload, listing, update and deletion of documents.
 */
@Injectable({
  providedIn: 'root'
})
export class DocumentService {

  private http = inject(HttpClient);
  private readonly BASE_URL = `${environment.apiBaseUrl}/api/documents`;

  /**
   * Fetches all documents.
   * @returns Observable of DocumentResponse array
   */
  getAll(): Observable<DocumentResponse[]> {
    return this.http.get<DocumentResponse[]>(this.BASE_URL);
  }

  /**
   * Uploads a new document with metadata.
   * Uses multipart/form-data.
   *
   * @param file the PDF or Word file
   * @param title document title
   * @param description optional description
   * @param roleAccess minimum role required to access this document
   * @returns Observable of created DocumentResponse
   */
  upload(
    file: File,
    title: string,
    description: string,
    roleAccess: string
  ): Observable<DocumentResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    formData.append('description', description);
    formData.append('roleAccess', roleAccess);
    return this.http.post<DocumentResponse>(this.BASE_URL, formData);
  }

  /**
   * Updates document metadata.
   *
   * @param id document ID
   * @param request update payload
   * @returns Observable of updated DocumentResponse
   */
  update(id: number, request: DocumentUpdateRequest): Observable<DocumentResponse> {
    return this.http.put<DocumentResponse>(`${this.BASE_URL}/${id}`, request);
  }

  /**
   * Archives a document (soft delete).
   *
   * @param id document ID
   * @returns Observable of void
   */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.BASE_URL}/${id}`);
  }

  /**
   * Returns human-readable file size.
   * Example: 1048576 → "1.0 MB"
   *
   * @param bytes file size in bytes
   * @returns formatted string
   */
 formatFileSize(bytes: number): string {
  if (!bytes || bytes === 0) return '—';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}
}