package com.triobank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * @param expiresIn seconds until the token expires
 */
public record LoginResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "1800") long expiresIn,
        Instant expiresAt,
        CurrentUserDto user) {

    public static final String BEARER = "Bearer";

    /** Keeps the token out of logs. */
    @Override
    public String toString() {
        return "LoginResponse[accessToken=***, expiresAt=" + expiresAt + ", user=" + user.id() + "]";
    }

}
