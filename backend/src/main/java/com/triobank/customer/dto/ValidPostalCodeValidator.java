package com.triobank.customer.dto;

import com.triobank.common.error.FieldErrorCode;
import com.triobank.common.error.FieldErrors;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class ValidPostalCodeValidator implements ConstraintValidator<ValidPostalCode, AddressDto> {

    private static final Pattern POLISH_POSTAL_CODE = Pattern.compile("\\d{2}-\\d{3}");

    @Override
    public boolean isValid(AddressDto address, ConstraintValidatorContext context) {
        // A missing postal code is reported by @NotBlank on the field.
        if (address == null || address.postalCode() == null || address.postalCode().isBlank()
                || !AddressDto.DEFAULT_COUNTRY.equals(address.countryOrDefault())) {
            return true;
        }
        if (POLISH_POSTAL_CODE.matcher(address.postalCode()).matches()) {
            return true;
        }
        return FieldErrors.reject(context, "postalCode", FieldErrorCode.INVALID_FORMAT,
                "Postal code must look like 00-000.");
    }

}
