package com.triobank.common.error;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Throw this from services when a request breaks a rule (not found, insufficient funds, ...).
 * Each entry in {@code properties} is added to the error body as an extra field, e.g. {@code lockedUntil}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> properties;

    public ApiException(ErrorCode code) {
        this(code, code.title(), Map.of());
    }

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public ApiException(ErrorCode code, String detail, Map<String, Object> properties) {
        super(detail);
        this.code = Objects.requireNonNull(code, "code");
        this.properties = Map.copyOf(properties);
    }

    /**
     * A {@code VALIDATION_ERROR} about one field that only the service can check, e.g. a field that is
     * required in some cases. The body looks the same as when request validation fails.
     */
    public static ApiException fieldError(String field, FieldErrorCode code, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, GlobalExceptionHandler.VALIDATION_DETAIL,
                Map.of(GlobalExceptionHandler.ERRORS, List.of(new FieldErrorDto(field, code, message))));
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

}
