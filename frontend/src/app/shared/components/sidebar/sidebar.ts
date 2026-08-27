import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../../core/services/auth';
import { TranslationService } from '../../../core/services/translation.service';
import { Role } from '../../../core/models/auth.model';

/**
 * Shared sidebar component used by both Admin and Chat layouts.
 * Displays navigation links based on user role.
 * Shows user profile and logout button at the bottom.
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    RouterLinkActive,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule
  ],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss'
})
export class SidebarComponent {

  authService = inject(AuthService);
  ts = inject(TranslationService);

  /** Expose Role enum to template */
  Role = Role;

  /** Current authenticated user */
  user = this.authService.getCurrentUser();

  /**
   * Returns the user's initials for the avatar.
   * Example: "Hamza El Rhadiouini" → "HE"
   */
  get initials(): string {
    if (!this.user?.fullName) return '?';
    return this.user.fullName
      .split(' ')
      .map(n => n[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  /**
   * Logs out the current user.
   */
  logout(): void {
    this.authService.logout();
  }

  /**
   * Toggles the application language between FR and EN.
   */
  toggleLanguage(): void {
    this.ts.toggleLanguage();
  }
}