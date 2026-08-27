package com.alten.user_service.dto;

import com.alten.user_service.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight user representation used in list views.
 * Contains only the fields needed for display — avoids over-fetching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {

    private Long id;
    private String email;
    private String fullName;
    private Role role;
    private boolean enabled;
}