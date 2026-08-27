package com.alten.auth_service.controller;

import com.alten.auth_service.dto.AuthResponse;
import com.alten.auth_service.dto.LoginRequest;
import com.alten.auth_service.dto.RegisterRequest;
import com.alten.auth_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing authentication endpoints.
 * All endpoints are public — no JWT required (configured in SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a new user and returns a JWT access token.
     *
     * POST /api/auth/register
     *
     * @param request validated registration payload
     * @return 201 CREATED with AuthResponse containing the JWT token
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates a user and returns a JWT access token.
     *
     * POST /api/auth/login
     *
     * @param request validated login payload
     * @return 200 OK with AuthResponse containing the JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}