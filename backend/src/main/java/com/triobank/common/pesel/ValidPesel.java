package com.triobank.common.pesel;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value must be a valid PESEL. {@code null} passes, add {@code @NotBlank} if the field is required.
 * Field error code: {@code INVALID_FORMAT}, {@code INVALID_CHECKSUM} or {@code INVALID_DATE}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPeselValidator.class)
public @interface ValidPesel {

    String message() default "PESEL is invalid.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
