package com.triobank.common.iban;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value must be a Polish IBAN. Spaces and lower case are allowed. {@code null} passes, add {@code @NotBlank} if
 * the field is required. Field error code: {@code INVALID_FORMAT} or {@code INVALID_CHECKSUM}.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidIbanValidator.class)
public @interface ValidIban {

    String message() default "IBAN is invalid.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
