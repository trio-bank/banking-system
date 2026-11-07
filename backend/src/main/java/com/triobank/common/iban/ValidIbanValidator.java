package com.triobank.common.iban;

import com.triobank.common.error.FieldErrorCode;
import com.triobank.common.error.FieldErrors;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidIbanValidator implements ConstraintValidator<ValidIban, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return switch (IbanValidator.check(value)) {
            case VALID -> true;
            case INVALID_FORMAT -> FieldErrors.reject(context, FieldErrorCode.INVALID_FORMAT,
                    "IBAN must be PL followed by 26 digits.");
            case INVALID_CHECKSUM -> FieldErrors.reject(context, FieldErrorCode.INVALID_CHECKSUM,
                    "IBAN check digits are invalid.");
        };
    }

}
