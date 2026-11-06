package com.triobank.common.error;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which error code to return when a DB constraint fails. Services check these rules themselves;
 * this only catches the case when two requests pass the check at the same time.
 * Add an entry together with the migration that creates the constraint.
 */
final class ConstraintErrorCodes {

    private static final Map<String, ErrorCode> BY_CONSTRAINT = Map.of(
            // e.g. "uk_users_email", ErrorCode.EMAIL_ALREADY_REGISTERED
    );

    private ConstraintErrorCodes() {
    }

    static Optional<ErrorCode> forConstraint(String constraintName) {
        if (constraintName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_CONSTRAINT.get(constraintName.toLowerCase(Locale.ROOT)));
    }

}
