package com.triobank.account.dto;

import com.triobank.account.AccountStatus;
import com.triobank.common.money.Money;
import com.triobank.product.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A customer account as the customer sees it.
 */
public record AccountDto(
        UUID id,
        @Schema(example = "PL45999000121000000000000001") String iban,
        @Schema(example = "Trio Personal Account") String name,
        AccountType type,
        @Schema(example = "PERSONAL_STANDARD") String productCode,
        @Schema(example = "Trio Personal Account") String productName,
        @Schema(example = "PLN") String currency,
        @Schema(type = "string", example = "0.00") @Money BigDecimal balance,
        AccountStatus status,
        @Schema(type = "string", example = "0.0000", description = "% per year") BigDecimal interestRate,
        @Schema(type = "string", example = "0.012345", nullable = true, description = "Only for savings accounts")
        BigDecimal accruedInterest,
        Instant openedAt) {
}
