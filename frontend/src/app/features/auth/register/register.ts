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
import { MatSelectModule } from '@angular/material/select';
import { AuthService } from '../../../core/services/auth';
import { Role } from '../../../core/models/auth.model';

/**
 * Register page component.
 * Handles new user registration with role selection.
 * On success, redirects to the dashboard.
 */
@Component({
  selector: 'app-register',
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
    MatIconModule,
    MatSelectModule
  ],
  templateUrl: './register.html',
  styleUrl: './register.scss'
})
export class RegisterComponent {

  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  hidePassword = true;
  isLoading = false;
  errorMessage = '';

  /** Available roles for selection */
  roles = [
    { value: Role.OPERATIONNEL, label: 'Opérationnel' },
    { value: Role.EXPERT, label: 'Expert' },
   
  ];

  registerForm: FormGroup = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: [Role.OPERATIONNEL, Validators.required]
  });

  /**
   * Submits the registration form.
   * Validates form, calls AuthService, handles success and error.
   */
onSubmit(): void {
  if (this.registerForm.invalid) return;

  this.isLoading = true;
  this.errorMessage = '';

  this.authService.register(this.registerForm.value).subscribe({
    next: () => {
      this.isLoading = false;
    },
    error: (err) => {
      this.isLoading = false;
      this.errorMessage = err.status === 409
        ? 'Cet email est déjà utilisé'
        : 'Une erreur est survenue. Veuillez réessayer.';
    }
  });
}
}