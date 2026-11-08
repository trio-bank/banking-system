package com.triobank.auth;

import com.triobank.common.error.FieldErrorCode;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put on a request that has {@code email} and {@code password}: the password must not contain the part of the email
 * before "@". The error is reported on the {@code password} field with code {@code WEAK_PASSWORD}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordWithoutEmailValidator.class)
public @interface PasswordWithoutEmail {

    String message() default "Password must not contain your email name.";

    FieldErrorCode errorCode() default FieldErrorCode.WEAK_PASSWORD;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
