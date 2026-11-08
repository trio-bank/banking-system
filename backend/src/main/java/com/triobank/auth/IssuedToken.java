package com.triobank.auth;

import java.time.Instant;

/**
 * A freshly signed access token.
 *
 * @param value     the compact JWT to send as {@code Authorization: Bearer <value>}
 * @param issuedAt  when the token was signed, in whole seconds
 * @param expiresAt when the token stops being accepted
 */
public record IssuedToken(String value, Instant issuedAt, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedToken[value=***, issuedAt=" + issuedAt + ", expiresAt=" + expiresAt + "]";
    }

}
