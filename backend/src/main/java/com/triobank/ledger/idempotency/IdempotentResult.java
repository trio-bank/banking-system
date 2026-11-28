package com.triobank.ledger.idempotency;

/**
 * Result of a money request. {@code replayed} is true when the request had already been done before
 * and the stored result is returned: the controller then answers 200 instead of 201.
 */
public record IdempotentResult<T>(T value, boolean replayed) {

    static <T> IdempotentResult<T> created(T value) {
        return new IdempotentResult<>(value, false);
    }

    static <T> IdempotentResult<T> replayed(T value) {
        return new IdempotentResult<>(value, true);
    }

}
