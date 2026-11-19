package com.triobank.ledger;

import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The {@code Idempotency-Key} header of money operations. The client makes a UUID when the confirmation
 * screen opens and sends the same one again on retries.
 * Controllers take the header with {@code required = false} and pass it to {@link #require(String)},
 * so a missing header gives our own error code and not Spring's generic 400.
 */
public final class IdempotencyKey {

    public static final String HEADER = "Idempotency-Key";

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private IdempotencyKey() {
    }

    /**
     * Checks the header value and returns it in lower case, so the same UUID in upper case is the same key.
     *
     * @throws ApiException {@code IDEMPOTENCY_KEY_MISSING} if the header is missing or not a UUID
     */
    public static String require(String header) {
        String key = header == null ? "" : header.strip();
        if (!UUID_FORMAT.matcher(key).matches()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_MISSING,
                    "Header " + HEADER + " must contain a UUID.");
        }
        return key.toLowerCase(Locale.ROOT);
    }

}
