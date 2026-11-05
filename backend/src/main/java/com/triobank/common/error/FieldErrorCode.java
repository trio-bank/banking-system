package com.triobank.common.error;

/**
 * Error codes for single fields, sent in {@code errors[].code}.
 */
public enum FieldErrorCode {
    REQUIRED,
    INVALID_FORMAT,
    TOO_SHORT,
    TOO_LONG,
    OUT_OF_RANGE,
    INVALID_CHECKSUM,
    WEAK_PASSWORD,
    TOO_MANY_DECIMALS,
    INVALID_DATE
}
