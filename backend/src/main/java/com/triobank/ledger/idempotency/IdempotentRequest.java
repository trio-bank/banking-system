package com.triobank.ledger.idempotency;

import java.util.Objects;
import java.util.UUID;

/**
 * Who sent a money request, with which idempotency key, and the hash of what was sent.
 * Use the same key and hash in the {@link PostingCommand}.
 */
public record IdempotentRequest(UUID userId, String key, String requestHash) {

    public IdempotentRequest {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestHash, "requestHash");
    }

}
