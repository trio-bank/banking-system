package com.triobank.auth.dto;

import com.triobank.auth.Role;
import com.triobank.auth.UserStatus;
import com.triobank.customer.dto.CustomerProfileDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * @param lastLoginAt the login before the current one, shown as "Last login"
 * @param customer    profile of a customer; null for admins and in the login response
 */
public record CurrentUserDto(
        UUID id,
        @Schema(example = "anna.nowak@example.com") String email,
        Role role,
        UserStatus status,
        @Schema(nullable = true) Instant lastLoginAt,
        @Schema(nullable = true) CustomerProfileDto customer) {
}
