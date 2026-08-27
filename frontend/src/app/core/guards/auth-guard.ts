import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenService } from '../services/token';

/**
 * Route guard that protects routes requiring authentication.
 * If no JWT token exists, redirects to the login page.
 *
 * Usage in routes:
 * { path: 'dashboard', canActivate: [authGuard], component: DashboardComponent }
 */
export const authGuard: CanActivateFn = () => {
  const tokenService = inject(TokenService);
  const router = inject(Router);

  if (tokenService.hasToken()) {
    return true;
  }

  // No token found — redirect to login
  router.navigate(['/auth/login']);
  return false;
};