package com.triobank.payment.dto;

import com.triobank.common.money.Money;
import com.triobank.ledger.Channel;
import com.triobank.ledger.TransactionStatus;
import com.triobank.ledger.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A transaction as a customer sees it.
 */
public record TransactionDto(
        UUID id,
        @Schema(example = "TB202610240000000123") String reference,
        TransactionType type,
        TransactionStatus status,
        Channel channel,
        @Schema(type = "string", example = "200.00") @Money BigDecimal amount,
        @Schema(example = "PLN") String currency,
        @Schema(example = "ATM cash deposit") String title,
        PartyDto sender,
        PartyDto recipient,
        Instant createdAt,
        @Schema(nullable = true) Instant bookedAt) {
}
