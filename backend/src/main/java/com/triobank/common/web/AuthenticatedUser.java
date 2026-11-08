package com.triobank.common.web;

import com.triobank.auth.Role;
import java.util.Objects;
import java.util.UUID;

/**
 * The caller of the current request, taken from the access token. Pass the ids to services,
 * not this object or anything from Spring Security.
 *
 * @param customerId null for admins
 */
public record AuthenticatedUser(UUID userId, Role role, UUID customerId) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

}
