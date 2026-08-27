import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
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
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenService = inject(TokenService);
  const token = tokenService.getToken();

  // If no token exists, forward the request unchanged
  if (!token) {
    return next(req);
  }

  // Clone the request and add the Authorization header
  const authenticatedRequest = req.clone({
    headers: req.headers.set('Authorization', `Bearer ${token}`)
  });

  return next(authenticatedRequest);
};