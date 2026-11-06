package com.triobank.common.error;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * All API error codes. The constant name is sent to the client in the {@code code} field.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    IDEMPOTENCY_KEY_MISSING(HttpStatus.BAD_REQUEST, "Idempotency key missing"),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Not authenticated"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),

    USER_LOCKED(HttpStatus.FORBIDDEN, "User locked"),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "User blocked"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email already registered"),
    PESEL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "PESEL already registered"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key reused"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Invalid state transition"),

    CUSTOMER_UNDERAGE(HttpStatus.UNPROCESSABLE_ENTITY, "Customer underage"),
    PRODUCT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Product not available"),
    ACCOUNT_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "Account limit reached"),
    PREREQUISITE_ACCOUNT_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Prerequisite account required"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds"),
    SOURCE_ACCOUNT_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Source account unavailable"),
    RECIPIENT_ACCOUNT_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Recipient account unavailable"),
    RECIPIENT_ACCOUNT_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY, "Recipient account not found"),
    EXTERNAL_TRANSFER_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "External transfer not supported"),
    SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY, "Same account transfer"),
    TRANSFER_POLICY_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Transfer policy violation"),
    CURRENCY_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "Currency not supported"),
    CASH_AMOUNT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Cash amount invalid"),
    OPERATION_NOT_ALLOWED_FOR_ACCOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "Operation not allowed for account"),
    SELF_ACTION_FORBIDDEN(HttpStatus.UNPROCESSABLE_ENTITY, "Self action forbidden"),

    FEATURE_DISABLED(HttpStatus.NOT_FOUND, "Feature disabled"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private static final String TYPE_BASE = "https://triobank.example/errors/";

    private final HttpStatus status;
    private final String title;
    private final URI type;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
        this.type = URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** E.g. {@code https://triobank.example/errors/insufficient-funds}. */
    public URI type() {
        return type;
    }

}
