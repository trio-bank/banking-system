package com.triobank.common.error;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Finds out which database constraint failed, e.g. {@code uk_users_email}.
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** Name of the violated constraint somewhere in the cause chain, or {@code null} if there is none. */
    public static String nameOf(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    /** Whether the exception was caused by the given constraint (names are compared ignoring case). */
    public static boolean isViolationOf(Throwable ex, String constraintName) {
        return constraintName.equalsIgnoreCase(nameOf(ex));
    }

}
