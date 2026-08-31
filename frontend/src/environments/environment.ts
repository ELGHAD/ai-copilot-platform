/**
 * Development environment.
 *
 * apiBaseUrl is deliberately empty: services build relative URLs such as
 * `/api/users`, which the Angular dev-server proxies to the gateway on :8080
 * (see proxy.conf.json). Same-origin, so no CORS preflight in development.
 *
 * Set it to an absolute origin (e.g. 'http://localhost:8080') only if you need
 * to bypass the proxy and talk to the gateway directly.
 */
export const environment = {
  production: false,
  apiBaseUrl: '',
};
