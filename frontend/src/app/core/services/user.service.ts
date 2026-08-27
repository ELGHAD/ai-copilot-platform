import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Role } from '../models/auth.model';

export interface UserResponse {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface UpdateUserRequest {
  fullName: string;
  role: Role;
  enabled: boolean;
}

/**
 * Service that communicates with user-service backend.
 * Handles listing, updating and disabling users.
 */
@Injectable({
  providedIn: 'root'
})
export class UserService {

  private http = inject(HttpClient);
  private readonly BASE_URL = 'http://localhost:8082/api/users';

  /**
   * Fetches all users.
   * @returns Observable of UserResponse array
   */
  getAll(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(this.BASE_URL);
  }

  /**
   * Updates a user's fullName, role and enabled status.
   *
   * @param id user ID
   * @param request update payload
   * @returns Observable of updated UserResponse
   */
  update(id: number, request: UpdateUserRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${this.BASE_URL}/${id}`, request);
  }

  /**
   * Disables a user account.
   *
   * @param id user ID
   * @returns Observable of void
   */
  disable(id: number): Observable<void> {
    return this.http.delete<void>(`${this.BASE_URL}/${id}`);
  }

  /**
   * Returns CSS color for each role badge.
   */
  getRoleColor(role: Role): string {
    switch (role) {
      case Role.ADMIN: return '#e94560';
      case Role.EXPERT: return '#0f3460';
      case Role.OPERATIONNEL: return '#2e7d32';
      default: return '#888';
    }
  }
}