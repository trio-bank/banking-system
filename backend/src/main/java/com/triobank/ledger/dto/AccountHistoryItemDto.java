package com.triobank.ledger.dto;

import com.triobank.common.money.Money;
import com.triobank.ledger.Channel;
import com.triobank.ledger.TransactionStatus;
import com.triobank.ledger.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of an account history: one ledger entry of the account together with its transaction.
 *
 * @param counterpartyName the other party as written when the transaction was made, "Trio Bank" for bank accounts
 * @param counterpartyIban null when the other party is one of the bank's own accounts
 */
public record AccountHistoryItemDto(
        UUID entryId,
        UUID transactionId,
        @Schema(example = "TB202610240000000123") String reference,
        TransactionType type,
        TransactionStatus status,
        Channel channel,
        Direction direction,
        @Schema(type = "string", example = "150.00") @Money BigDecimal amount,
        @Schema(example = "PLN") String currency,
        @Schema(type = "string", example = "850.00") @Money BigDecimal balanceAfter,
        @Schema(example = "Dinner") String title,
        @Schema(example = "Jan Kowalski") String counterpartyName,
        @Schema(example = "PL18999000121000000000000002", nullable = true) String counterpartyIban,
        Instant createdAt,
        @Schema(nullable = true) Instant bookedAt) {
}
