import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth';

/**
 * Login page component.
 * Handles user authentication via email and password.
 * On success, redirects to the dashboard.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class LoginComponent {
  private resolveErrorMessage(err: any): string {
  switch (err.status) {
    case 401: return 'Email ou mot de passe incorrect';
    case 403: return 'Compte désactivé. Contactez un administrateur.';
    case 0: return 'Impossible de contacter le serveur.';
    default: return 'Une erreur est survenue. Veuillez réessayer.';
  }
}
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  /** Controls password visibility toggle */
  hidePassword = true;

  /** Loading state — disables form during API call */
  isLoading = false;

  /** Error message displayed below the form */
  errorMessage = '';

  loginForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  /**
   * Submits the login form.
   * Validates form, calls AuthService, handles success and error.
   */
  onSubmit(): void {
  if (this.loginForm.invalid) return;

  this.isLoading = true;
  this.errorMessage = '';

  this.authService.login(this.loginForm.value).subscribe({
    next: () => {
      this.isLoading = false;
    },
    error: (err) => {
      this.isLoading = false;
      this.errorMessage = this.resolveErrorMessage(err);
    }
  });
}
  
}