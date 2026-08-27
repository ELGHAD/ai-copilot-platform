package com.alten.auth_service.service;

import com.alten.auth_service.dto.AuthResponse;
import com.alten.auth_service.dto.LoginRequest;
import com.alten.auth_service.dto.RegisterRequest;
import com.alten.auth_service.model.User;
import com.alten.auth_service.repository.UserRepository;
import com.alten.auth_service.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Business logic for user authentication.
 * Handles registration and login, delegating token generation to JwtUtil.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * Registers a new user after validating email uniqueness.
     * Password is hashed with BCrypt before persistence.
     *
     * @param request registration payload containing email, password, fullName and role
     * @return AuthResponse containing the JWT access token and user info
     * @throws IllegalStateException if the email is already registered
     */
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    /**
     * Authenticates a user with email and password.
     * Spring Security's AuthenticationManager handles credential validation.
     *
     * @param request login payload containing email and password
     * @return AuthResponse containing the JWT access token and user info
     * @throws org.springframework.security.authentication.BadCredentialsException if credentials are invalid
     */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }
}