package com.alten.user_service.service;

import com.alten.user_service.dto.UpdateUserRequest;
import com.alten.user_service.dto.UserResponse;
import com.alten.user_service.dto.UserSummary;
import com.alten.user_service.model.Role;
import com.alten.user_service.model.User;
import com.alten.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for user profile management.
 * Handles fetching, updating, and listing users.
 * All write operations are restricted to ADMIN role (enforced in controller).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Retrieves the full profile of the currently authenticated user.
     *
     * @param email extracted from the JWT token by the security filter
     * @return full UserResponse for the authenticated user
     * @throws IllegalStateException if no user is found for the given email
     */
    public UserResponse getCurrentUser(String email) {
        User user = findUserByEmailOrThrow(email);
        return toUserResponse(user);
    }

    /**
     * Retrieves the full profile of any user by ID.
     * Restricted to ADMIN role.
     *
     * @param id the user's database ID
     * @return full UserResponse for the requested user
     * @throws IllegalStateException if no user is found for the given ID
     */
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found with id: " + id));
        return toUserResponse(user);
    }
    /**
     * Returns platform statistics for the admin dashboard.
     * Counts total users, active users, and users by role.
     *
     * @return map of statistic name to count
     */
    public Map<String, Long> getPlatformStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("activeUsers", userRepository.countByEnabled(true));
        stats.put("adminCount", userRepository.countByRole(Role.ADMIN));
        stats.put("expertCount", userRepository.countByRole(Role.EXPERT));
        stats.put("operationnelCount", userRepository.countByRole(Role.OPERATIONNEL));
        return stats;
    }

    /**
     * Retrieves a summary list of all users.
     * Restricted to ADMIN role.
     *
     * @return list of UserSummary for all users in the system
     */
    public List<UserSummary> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toUserSummary)
                .toList();
    }

    /**
     * Updates a user's fullName, role, and enabled status.
     * Restricted to ADMIN role.
     *
     * @param id      the user's database ID
     * @param request update payload containing fullName, role, and enabled
     * @return updated UserResponse
     * @throws IllegalStateException if no user is found for the given ID
     */
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found with id: " + id));

        user.setFullName(request.getFullName());
        user.setRole(request.getRole());
        user.setEnabled(request.getEnabled());

        userRepository.save(user);
        return toUserResponse(user);
    }

    /**
     * Disables a user account without deleting it.
     * Restricted to ADMIN role.
     *
     * @param id the user's database ID
     * @throws IllegalStateException if no user is found for the given ID
     */
    public void disableUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found with id: " + id));
        user.setEnabled(false);
        userRepository.save(user);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Finds a user by email or throws a descriptive exception.
     */
    private User findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found with email: " + email));
    }

    /**
     * Maps a User entity to a full UserResponse DTO.
     */
    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Maps a User entity to a lightweight UserSummary DTO.
     */
    private UserSummary toUserSummary(User user) {
        return UserSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();
    }
}