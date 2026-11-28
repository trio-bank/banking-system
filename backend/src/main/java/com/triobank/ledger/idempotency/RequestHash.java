package com.triobank.ledger.idempotency;

import com.triobank.common.money.MoneyUtils;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a money request, stored next to its idempotency key. A retry with the same key must have
 * the same hash, otherwise the key is being reused for a different request.
 */
public final class RequestHash {

    private RequestHash() {
    }

    /**
     * Hashes the operation together with the request fields. The operation is part of the hash,
     * so the same body sent to a deposit and to a withdrawal does not count as the same request.
     * Amounts are taken with 2 decimals, so {@code 200} and {@code 200.00} give the same hash.
     *
     * @param operation what the request does, e.g. {@code ATM_DEPOSIT}
     * @param fields    request fields in a fixed order; {@code null} is allowed
     * @return 64 lower-case hex characters
     */
    public static String of(String operation, Object... fields) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, operation);
        for (Object field : fields) {
            append(canonical, field instanceof BigDecimal amount ? MoneyUtils.normalize(amount).toPlainString() : field);
        }
        return HexFormat.of().formatHex(sha256(canonical.toString()));
    }

    // Each value is written with its length in front, so ("ab", "c") and ("a", "bc") never look the same.
    private static void append(StringBuilder canonical, Object value) {
        if (value == null) {
            canonical.append("-;");
            return;
        }
        String text = value.toString();
        canonical.append(text.length()).append(':').append(text).append(';');
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }

}
