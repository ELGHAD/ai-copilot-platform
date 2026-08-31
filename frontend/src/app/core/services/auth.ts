import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.model';
import { User } from '../models/user.model';
import { TokenService } from './token';
import { Role } from '../models/auth.model';
import { environment } from '../../../environments/environment';
/**
 * Handles all authentication operations.
 * Communicates with the backend via the gateway on port 8080.
 * Manages the current user state via a BehaviorSubject.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly API_URL = `${environment.apiBaseUrl}/api/auth`;

  // ─── Dependencies injected via inject() ───────────────────────────────────
  private tokenService = inject(TokenService);
  private http = inject(HttpClient);
  private router = inject(Router);

  /**
   * Holds the current authenticated user.
   * null means no user is logged in.
   * Components subscribe to this to react to auth state changes.
   * Initialized from localStorage so state survives page refresh.
   */
  private currentUserSubject = new BehaviorSubject<User | null>(
    this.tokenService.getUser() as User | null
  );

  currentUser$ = this.currentUserSubject.asObservable();

  /**
   * Sends login credentials to the backend.
   * On success, saves the token and user info, then updates currentUser$.
   *
   * @param request login payload containing email and password
   * @returns Observable of AuthResponse
   */
login(request: LoginRequest): Observable<AuthResponse> {
  return this.http.post<AuthResponse>(`${this.API_URL}/login`, request).pipe(
    tap(response => {
      this.handleAuthSuccess(response);
      this.redirectByRole(response.role);
    })
  );
}

register(request: RegisterRequest): Observable<AuthResponse> {
  return this.http.post<AuthResponse>(`${this.API_URL}/register`, request).pipe(
    tap(response => {
      this.handleAuthSuccess(response);
      this.redirectByRole(response.role);
    })
  );
}

/**
 * Redirects the user to the appropriate page based on their role.
 * ADMIN → /admin/dashboard
 * EXPERT/OPERATIONNEL → /chat
 */
private redirectByRole(role: Role): void {
  switch (role) {
    case Role.ADMIN:
      this.router.navigate(['/admin/dashboard']);
      break;
    case Role.EXPERT:
    case Role.OPERATIONNEL:
      this.router.navigate(['/chat']);
      break;
    default:
      this.router.navigate(['/auth/login']);
  }
}

  /**
   * Logs out the current user.
   * Clears all stored auth data and redirects to login page.
   */
  logout(): void {
    this.tokenService.clearAll();
    this.currentUserSubject.next(null);
    this.router.navigate(['/auth/login']);
  }

  /**
   * Returns the current authenticated user synchronously.
   * @returns the current user or null
   */
  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * Checks whether a user is currently authenticated.
   * @returns true if a token exists
   */
  isAuthenticated(): boolean {
    return this.tokenService.hasToken();
  }

  // ─── Private helpers ──────────────────────────────────────────────────────

  /**
   * Handles successful authentication response.
   * Saves token + user info and updates the currentUser$ stream.
   */
  private handleAuthSuccess(response: AuthResponse): void {
    this.tokenService.saveToken(response.accessToken);

    const user: User = {
      email: response.email,
      fullName: response.fullName,
      role: response.role
    };

    this.tokenService.saveUser(user);
    this.currentUserSubject.next(user);
  }
}