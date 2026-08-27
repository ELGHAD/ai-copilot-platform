import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenService } from '../services/token';
import { AuthService } from '../services/auth';
import { Role } from '../models/auth.model';

/**
 * Route guard that protects admin-only routes.
 * Redirects non-admin users to /chat.
 */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.getCurrentUser();

  if (user?.role === Role.ADMIN) {
    return true;
  }

  router.navigate(['/chat']);
  return false;
};