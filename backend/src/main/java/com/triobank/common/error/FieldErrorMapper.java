package com.triobank.common.error;

import jakarta.validation.ConstraintViolation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Turns validation failures into {@code errors[]} entries.
 * A custom constraint can set its own code with an attribute, e.g.
 * {@code FieldErrorCode errorCode() default FieldErrorCode.INVALID_CHECKSUM;}.
 */
final class FieldErrorMapper {

    static final String ERROR_CODE_ATTRIBUTE = "errorCode";

    private FieldErrorMapper() {
    }

    static List<FieldErrorDto> fromBindingResult(BindingResult result) {
        List<FieldErrorDto> errors = new ArrayList<>();
        for (ObjectError error : result.getAllErrors()) {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            ConstraintViolation<?> violation = error.contains(ConstraintViolation.class)
                    ? error.unwrap(ConstraintViolation.class)
                    : null;
            errors.add(new FieldErrorDto(field, codeFor(error, violation), error.getDefaultMessage()));
        }
        return errors;
    }

    static List<FieldErrorDto> fromMethodValidation(HandlerMethodValidationException ex) {
        List<FieldErrorDto> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameter = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = error instanceof FieldError fieldError
                        ? parameter + "." + fieldError.getField()
                        : parameter;
                ConstraintViolation<?> violation = unwrapViolation(result, error);
                errors.add(new FieldErrorDto(field, codeFor(error, violation), error.getDefaultMessage()));
            }
        }
        return errors;
    }

    private static ConstraintViolation<?> unwrapViolation(ParameterValidationResult result,
                                                          MessageSourceResolvable error) {
        try {
            return result.unwrap(error, ConstraintViolation.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static FieldErrorCode codeFor(MessageSourceResolvable error, ConstraintViolation<?> violation) {
        if (violation != null) {
            Map<String, Object> attributes = violation.getConstraintDescriptor().getAttributes();
            if (attributes.get(ERROR_CODE_ATTRIBUTE) instanceof FieldErrorCode custom) {
                return custom;
            }
            String constraint = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            return codeFor(constraint, violation.getInvalidValue(), attributes);
        }
        // No constraint behind it, e.g. "abc" sent for a number.
        String[] codes = error.getCodes();
        String constraint = codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
        return codeFor(constraint, null, Map.of());
    }

    private static FieldErrorCode codeFor(String constraint, Object value, Map<String, Object> attributes) {
        return switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> FieldErrorCode.REQUIRED;
            case "Size", "Length" -> isShorterThanMin(value, attributes)
                    ? FieldErrorCode.TOO_SHORT
                    : FieldErrorCode.TOO_LONG;
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
                 "Negative", "NegativeOrZero", "Range" -> FieldErrorCode.OUT_OF_RANGE;
            case "Digits" -> FieldErrorCode.TOO_MANY_DECIMALS;
            case "Past", "PastOrPresent", "Future", "FutureOrPresent" -> FieldErrorCode.INVALID_DATE;
            default -> FieldErrorCode.INVALID_FORMAT;
        };
    }

    private static boolean isShorterThanMin(Object value, Map<String, Object> attributes) {
        if (!(attributes.get("min") instanceof Integer min)) {
            return false;
        }
        int length = switch (value) {
            case CharSequence text -> text.length();
            case Collection<?> collection -> collection.size();
            case Map<?, ?> map -> map.size();
            case Object[] array -> array.length;
            case null, default -> Integer.MAX_VALUE;
        };
        return length < min;
    }

}
