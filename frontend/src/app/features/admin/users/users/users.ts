import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SidebarComponent } from '../../../../shared/components/sidebar/sidebar';
import { UserService, UserResponse } from '../../../../core/services/user.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { AuthService } from '../../../../core/services/auth';
import { Role } from '../../../../core/models/auth.model';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    SidebarComponent
  ],
  templateUrl: './users.html',
  styleUrl: './users.scss'
})
export class UsersComponent implements OnInit {

  private userService = inject(UserService);
  ts = inject(TranslationService);
  private authService = inject(AuthService);

  users = signal<UserResponse[]>([]);
  isLoading = signal(true);
  error = signal('');
  successMessage = signal('');

  /** Edit modal state */
  showEditModal = signal(false);
  editingUser = signal<UserResponse | null>(null);

  /** Filter */
  selectedRole = signal<string>('ALL');

  /** Edit form */
  editFullName = '';
  editRole: Role = Role.OPERATIONNEL;
  editEnabled = true;

  roles = [Role.ADMIN, Role.EXPERT, Role.OPERATIONNEL];
  filterOptions = ['ALL', ...Object.values(Role)];

  /** Current logged-in user email — to prevent self-disable */
  currentUserEmail = this.authService.getCurrentUser()?.email;

  ngOnInit(): void {
    this.loadUsers();
  }

  /**
   * Loads all users from the backend.
   */
  loadUsers(): void {
    this.isLoading.set(true);
    this.error.set('');

    this.userService.getAll().subscribe({
      next: (users) => {
        this.users.set(users);
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set(this.ts.t().common_error);
        this.isLoading.set(false);
      }
    });
  }

  /**
   * Returns users filtered by selected role.
   */
  get filteredUsers(): UserResponse[] {
    const role = this.selectedRole();
    if (role === 'ALL') return this.users();
    return this.users().filter(u => u.role === role);
  }

  /**
   * Opens the edit modal for a user.
   */
  openEditModal(user: UserResponse): void {
    this.editingUser.set(user);
    this.editFullName = user.fullName;
    this.editRole = user.role;
    this.editEnabled = user.enabled;
    this.showEditModal.set(true);
  }

  /**
   * Saves user changes.
   */
  saveEdit(): void {
    const user = this.editingUser();
    if (!user) return;

    this.userService.update(user.id, {
      fullName: this.editFullName,
      role: this.editRole,
      enabled: this.editEnabled
    }).subscribe({
      next: (updated) => {
        this.users.update(users =>
          users.map(u => u.id === updated.id ? updated : u)
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
   * Disables a user account.
   * Prevents disabling own account.
   */
  disableUser(user: UserResponse): void {
    if (user.email === this.currentUserEmail) {
      this.error.set('Vous ne pouvez pas désactiver votre propre compte');
      return;
    }

    if (!confirm(this.ts.t().users_disable_confirm)) return;

    this.userService.disable(user.id).subscribe({
      next: () => {
        this.users.update(users =>
          users.map(u => u.id === user.id ? { ...u, enabled: false } : u)
        );
        this.showSuccess(this.ts.t().common_success);
      },
      error: () => {
        this.error.set(this.ts.t().common_error);
      }
    });
  }

  /**
   * Returns initials from full name.
   */
  getInitials(fullName: string): string {
    return fullName
      .split(' ')
      .map(n => n[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  getRoleColor(role: Role): string {
    return this.userService.getRoleColor(role);
  }

  private showSuccess(message: string): void {
    this.successMessage.set(message);
    setTimeout(() => this.successMessage.set(''), 3000);
  }
}