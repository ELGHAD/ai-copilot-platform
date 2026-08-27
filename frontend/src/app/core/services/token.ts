import { Injectable } from '@angular/core';

/**
 * Manages JWT token storage and retrieval from localStorage.
 * Single responsibility: token persistence only.
 */
@Injectable({
  providedIn: 'root'
})
export class TokenService {

  private readonly TOKEN_KEY = 'alten_access_token';
  private readonly USER_KEY = 'alten_user';

  /**
   * Saves the JWT access token to localStorage.
   * @param token the JWT access token string
   */
  saveToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  /**
   * Retrieves the JWT access token from localStorage.
   * @returns the token string or null if not found
   */
  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  /**
   * Removes the JWT token from localStorage.
   * Called on logout.
   */
  removeToken(): void {
    localStorage.removeItem(this.TOKEN_KEY);
  }

  /**
   * Saves the authenticated user info to localStorage.
   * @param user the user object to persist
   */
  saveUser(user: object): void {
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  /**
   * Retrieves the authenticated user info from localStorage.
   * @returns the parsed user object or null if not found
   */
  getUser(): object | null {
    const user = localStorage.getItem(this.USER_KEY);
    return user ? JSON.parse(user) : null;
  }

  /**
   * Removes all auth data from localStorage.
   * Called on logout.
   */
  clearAll(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  /**
   * Checks whether a JWT token exists in localStorage.
   * Does not validate the token — only checks presence.
   * @returns true if a token exists
   */
  hasToken(): boolean {
    return this.getToken() !== null;
  }
}