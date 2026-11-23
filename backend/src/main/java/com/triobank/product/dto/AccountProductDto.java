package com.triobank.product.dto;

import com.triobank.product.AccountType;
import com.triobank.product.OutgoingTransferPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A product a customer can open an account of.
 */
public record AccountProductDto(
        UUID id,
        @Schema(example = "SAVINGS_STANDARD") String code,
        @Schema(example = "Trio Savings Account") String name,
        @Schema(example = "Savings account with interest. Transfers only to your own accounts.") String description,
        AccountType type,
        @Schema(example = "PLN") String currency,
        @Schema(type = "string", example = "4.0000", description = "% per year") BigDecimal interestRate,
        OutgoingTransferPolicy outgoingTransferPolicy,
        boolean atmAllowed,
        @Schema(example = "2", description = "How many not closed accounts of this product one customer may have")
        int maxAccountsPerCustomer,
        @Schema(nullable = true, description = "Account type the customer must already have active")
        AccountType prerequisiteType) {
}
