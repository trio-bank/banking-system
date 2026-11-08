package com.triobank.auth;

import com.triobank.auth.dto.RegisterRequest;
import com.triobank.common.error.FieldErrorCode;
import com.triobank.common.error.FieldErrors;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordWithoutEmailValidator implements ConstraintValidator<PasswordWithoutEmail, RegisterRequest> {

    @Override
    public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
        // A password that is weak anyway already has an error from @StrongPassword, one is enough.
        if (request == null || !PasswordPolicy.isStrong(request.password())
                || !PasswordPolicy.containsEmailName(request.password(), request.email())) {
            return true;
        }
        return FieldErrors.reject(context, "password", FieldErrorCode.WEAK_PASSWORD,
                "Password must not contain your email name.");
    }

}
