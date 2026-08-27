package com.alten.user_service.dto;

import com.alten.user_service.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload received when an ADMIN updates a user's profile.
 * Only fullName and role are updatable — email is immutable.
 */
@Data
public class UpdateUserRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotNull(message = "Role is required")
    private Role role;

    @NotNull(message = "Enabled status is required")
    private Boolean enabled;
}