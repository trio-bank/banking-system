package com.triobank.auth;

import java.time.Instant;

/**
 * A freshly signed access token.
 *
 * @param value     the compact JWT to send as {@code Authorization: Bearer <value>}
 * @param expiresAt when the token stops being accepted
 */
public record IssuedToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[value=***, expiresAt=" + expiresAt + "]";
    }

}
