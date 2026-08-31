/**
 * Production environment.
 *
 * Empty apiBaseUrl assumes the built bundle is served behind a reverse proxy
 * that forwards /api and /uploads to the gateway on the same origin. If the
 * gateway lives on a different origin, put that origin here — and make sure
 * CORS_ALLOWED_ORIGINS in the gateway config includes the frontend's origin.
 */
export const environment = {
  production: true,
  apiBaseUrl: '',
};
