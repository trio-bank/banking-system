package com.triobank.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cash put in or taken out at the ATM. ATM limits are checked by the service; here only the general
 * amount rules, so a broken amount is a 400 and an amount the ATM can't handle is a 422.
 *
 * @param currency leave it out or send null; anything other than PLN is rejected
 */
public record CashOperationRequest(
        @NotNull UUID accountId,
        @Schema(type = "string", example = "200.00")
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Schema(example = "PLN", nullable = true) String currency) {
}
