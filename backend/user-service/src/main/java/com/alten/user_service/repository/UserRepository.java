package com.alten.user_service.repository;

import com.alten.user_service.model.Role;
import com.alten.user_service.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for User entities in the user-service.
 * Shares the same users table created by auth-service.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their email address.
     * Used to fetch the current user's profile from the JWT subject.
     *
     * @param email the user's email address
     * @return an Optional containing the user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds all users with a specific role.
     * Used by ADMIN to filter users by role.
     *
     * @param role the role to filter by
     * @return list of users with the given role
     */
    List<User> findByRole(Role role);

    /**
     * Finds all enabled or disabled users.
     * Used by ADMIN to manage active/inactive accounts.
     *
     * @param enabled true for active users, false for disabled
     * @return list of users matching the enabled status
     */
    List<User> findByEnabled(boolean enabled);
    /**
     * Counts users by enabled status.
     */
    long countByEnabled(boolean enabled);

    /**
     * Counts users by role.
     */
    long countByRole(Role role);
}