package com.alten.auth_service.dto;

import com.alten.auth_service.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned to the client after successful authentication.
 * Contains the JWT access token and basic user information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /** JWT access token — valid for 15 minutes */
    private String accessToken;

    /** Token type, always Bearer */
    private String tokenType = "Bearer";

    private String email;
    private String fullName;
    private Role role;
}