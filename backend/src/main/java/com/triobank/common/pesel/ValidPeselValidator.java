package com.triobank.common.pesel;

import com.triobank.common.error.FieldErrorCode;
import com.triobank.common.error.FieldErrors;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPeselValidator implements ConstraintValidator<ValidPesel, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return switch (PeselValidator.check(value)) {
            case VALID -> true;
            case INVALID_FORMAT -> FieldErrors.reject(context, FieldErrorCode.INVALID_FORMAT,
                    "PESEL must have 11 digits.");
            case INVALID_CHECKSUM -> FieldErrors.reject(context, FieldErrorCode.INVALID_CHECKSUM,
                    "PESEL check digit is invalid.");
            case INVALID_DATE -> FieldErrors.reject(context, FieldErrorCode.INVALID_DATE,
                    "PESEL does not contain a valid date of birth.");
        };
    }

}
