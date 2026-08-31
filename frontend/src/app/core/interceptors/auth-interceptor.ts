import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { TokenService } from '../services/token';

/**
 * HTTP interceptor that automatically adds the JWT token
 * to every outgoing request as an Authorization header.
 *
 * This means components and services never need to manually
 * add the token — it's handled here globally.
 *
 * Example:
 * GET /api/users/me
 * becomes:
 * GET /api/users/me
 * Authorization: Bearer eyJhbGci...
 *
 * It also centralises expired-session handling. Access tokens live 15 minutes
 * (jwt.expiration=900000 in auth-service) and there is no refresh endpoint, so
 * once a token expires the gateway answers 401 with an empty body to every call.
 * Without the handler below, each feature component only saw "an error" and
 * rendered its generic red banner — the user was left on a dead page with no
 * indication that they simply had to sign in again.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenService = inject(TokenService);
  const router = inject(Router);
  const token = tokenService.getToken();

  // Login and register are excluded on purpose: they need no token, and a 401
  // there means "wrong credentials", not "session expired". Redirecting to the
  // login page from the login page would also wipe the error the user needs.
  const isAuthEndpoint = req.url.includes('/api/auth/');

  const request = token && !isAuthEndpoint
    ? req.clone({ headers: req.headers.set('Authorization', `Bearer ${token}`) })
    : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthEndpoint) {
        tokenService.clearAll();
        router.navigate(['/auth/login'], { queryParams: { expired: '1' } });
      }

      // Rethrow either way so component-level error handling still runs.
      return throwError(() => error);
    })
  );
};
