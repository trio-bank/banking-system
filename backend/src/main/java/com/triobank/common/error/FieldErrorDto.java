package com.triobank.common.error;

/**
 * One invalid field in a validation error.
 */
public record FieldErrorDto(String field, FieldErrorCode code, String message) {
}
