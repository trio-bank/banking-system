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
 * The password must follow {@link PasswordPolicy}. The "no email in the password" rule needs the email too, so it is
 * checked on the whole request, not here. {@code null} passes, add {@code @NotBlank} if the field is required.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {

    String message() default "Password must be 12–128 characters long and contain a lowercase letter, "
            + "an uppercase letter, a digit and a special character.";

    FieldErrorCode errorCode() default FieldErrorCode.WEAK_PASSWORD;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
