package com.alten.user_service.controller;

import com.alten.user_service.dto.UpdateUserRequest;
import com.alten.user_service.dto.UserResponse;
import com.alten.user_service.dto.UserSummary;
import com.alten.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing user profile management endpoints.
 *
 * Access rules:
 * - GET /api/users/me        → any authenticated user (own profile)
 * - GET /api/users           → ADMIN only (list all users)
 * - GET /api/users/{id}      → ADMIN only (any user profile)
 * - PUT /api/users/{id}      → ADMIN only (update user)
 * - DELETE /api/users/{id}   → ADMIN only (disable user)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Returns the profile of the currently authenticated user.
     * The email is extracted from the JWT token via the Authentication object.
     *
     * GET /api/users/me
     *
     * @param authentication injected by Spring Security from the JWT filter
     * @return 200 OK with the current user's full profile
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(userService.getCurrentUser(email));
    }

    /**
     * Returns a summary list of all users.
     * Restricted to ADMIN role.
     *
     * GET /api/users
     *
     * @return 200 OK with list of all user summaries
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSummary>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Returns the full profile of a specific user by ID.
     * Restricted to ADMIN role.
     *
     * GET /api/users/{id}
     *
     * @param id the user's database ID
     * @return 200 OK with the requested user's full profile
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Updates a user's fullName, role, and enabled status.
     * Restricted to ADMIN role.
     *
     * PUT /api/users/{id}
     *
     * @param id      the user's database ID
     * @param request validated update payload
     * @return 200 OK with the updated user profile
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    /**
     * Returns platform statistics for the admin dashboard.
     * Restricted to ADMIN role.
     *
     * GET /api/users/stats
     *
     * @return 200 OK with platform statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(userService.getPlatformStats());
    }

    /**
     * Disables a user account without deleting it.
     * Restricted to ADMIN role.
     *
     * DELETE /api/users/{id}
     *
     * @param id the user's database ID
     * @return 204 NO CONTENT on success
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> disableUser(@PathVariable Long id) {
        userService.disableUser(id);
        return ResponseEntity.noContent().build();
    }
}