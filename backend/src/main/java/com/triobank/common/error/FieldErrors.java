package com.triobank.common.error;

import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/**
 * For custom validators that can fail in more than one way, e.g. an IBAN with a wrong format or a wrong checksum.
 * The chosen code ends up in {@code errors[].code} of the response.
 */
public final class FieldErrors {

    private FieldErrors() {
    }

    /**
     * Replaces the default violation with one that has this code and message. Always returns {@code false},
     * so a validator can simply {@code return FieldErrors.reject(...)}.
     */
    public static boolean reject(ConstraintValidatorContext context, FieldErrorCode code, String message) {
        context.unwrap(HibernateConstraintValidatorContext.class).withDynamicPayload(code);
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }

}
