package com.triobank.auth;

/**
 * Whether the user can log in. PENDING_VERIFICATION and CLOSED aren't used yet.
 */
public enum UserStatus {

    PENDING_VERIFICATION,
    ACTIVE,
    LOCKED,
    BLOCKED,
    CLOSED

}
