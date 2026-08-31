package com.alten.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Key;
import java.util.Date;
import java.util.List;

/**
 * Global JWT filter applied to every request passing through the gateway.
 * Validates the JWT token before forwarding the request to the target service.
 * Public endpoints (register, login) are whitelisted and bypass this filter.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String secretKey;

    /** Endpoints that do not require a JWT token */
    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            // Ticket attachments are loaded by the browser as <img src="/uploads/...">.
            // A plain image request carries no Authorization header, so requiring a
            // token here would make every attachment render as a broken image.
            // chat-service also permits these without auth (see its SecurityConfig).
            "/uploads/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Allow public endpoints to pass through without token
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst("Authorization");

        // Reject requests with no Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return rejectUnauthorized(exchange);
        }

        String token = authHeader.substring(7);

        // Reject requests with invalid or expired token
        if (!isTokenValid(token)) {
            return rejectUnauthorized(exchange);
        }

        return chain.filter(exchange);
    }

    /**
     * Gateway filter runs first — before all other filters.
     */
    @Override
    public int getOrder() {
        return -1;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Checks whether the request path is a public endpoint.
     *
     * @param path the request URI path
     * @return true if the path is whitelisted
     */
    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith);
    }

    /**
     * Validates the JWT token — checks signature and expiration.
     *
     * @param token the JWT token string
     * @return true if the token is valid and not expired
     */
    private boolean isTokenValid(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Rejects the request with a 401 Unauthorized response.
     * No body — just the status code.
     */
    private Mono<Void> rejectUnauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}