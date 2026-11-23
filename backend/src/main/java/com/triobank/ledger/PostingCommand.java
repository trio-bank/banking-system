package com.triobank.ledger;

import com.triobank.common.money.MoneyConstants;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;

/**
 * Everything the ledger needs to move money from one account to another. The caller has already
 * resolved both accounts, checked its own rules and filled in the default title and names.
 *
 * @param initiatedByUserId customer or admin who started the operation; null for scheduled jobs
 * @param idempotencyKey    null for scheduled jobs; set together with {@code requestHash}
 * @param requestHash       SHA-256 of the request, hex
 */
@Builder
public record PostingCommand(
        TransactionType type,
        Channel channel,
        UUID sourceAccountId,
        UUID targetAccountId,
        BigDecimal amount,
        String currency,
        String title,
        String senderName,
        String recipientName,
        UUID initiatedByUserId,
        String idempotencyKey,
        String requestHash) {

    public PostingCommand {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(targetAccountId, "targetAccountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(recipientName, "recipientName");
        // amounts from requests are validated earlier; getting here with a bad one is a bug in the caller
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
        if (amount.stripTrailingZeros().scale() > MoneyConstants.SCALE) {
            throw new IllegalArgumentException("Amount has more than 2 decimals: " + amount);
        }
        if ((idempotencyKey == null) != (requestHash == null)) {
            throw new IllegalArgumentException("Idempotency key and request hash go together");
        }
    }

}
