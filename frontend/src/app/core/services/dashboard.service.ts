import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, map } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DashboardStats {
  totalUsers: number;
  activeUsers: number;
  adminCount: number;
  expertCount: number;
  operationnelCount: number;
  totalDocuments: number;
  activeDocuments: number;
  archivedDocuments: number;
  indexedDocuments: number;
}

/**
 * Service that fetches dashboard statistics from both
 * user-service and document-service in parallel.
 */
@Injectable({
  providedIn: 'root'
})
export class DashboardService {

  private http = inject(HttpClient);

  private readonly USER_STATS_URL = `${environment.apiBaseUrl}/api/users/stats`;
  private readonly DOC_STATS_URL = `${environment.apiBaseUrl}/api/documents/stats`;

  /**
   * Fetches user and document stats in parallel using forkJoin.
   * forkJoin waits for both requests to complete before emitting.
   *
   * @returns Observable of combined DashboardStats
   */
  getStats(): Observable<DashboardStats> {
    return forkJoin({
      users: this.http.get<any>(this.USER_STATS_URL),
      documents: this.http.get<any>(this.DOC_STATS_URL)
    }).pipe(
      map(({ users, documents }) => ({
        totalUsers: users.totalUsers,
        activeUsers: users.activeUsers,
        adminCount: users.adminCount,
        expertCount: users.expertCount,
        operationnelCount: users.operationnelCount,
        totalDocuments: documents.totalDocuments,
        activeDocuments: documents.activeDocuments,
        archivedDocuments: documents.archivedDocuments,
        indexedDocuments: documents.indexedDocuments
      }))
    );
  }
}