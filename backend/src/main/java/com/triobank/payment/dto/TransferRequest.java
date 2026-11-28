package com.triobank.payment.dto;

import com.triobank.common.iban.IbanValidator;
import com.triobank.common.iban.ValidIban;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Money sent from one of the caller's accounts to an account in Trio Bank, found by IBAN.
 * The fields are cleaned up here, before validation: spaces are taken out of the IBAN, the title and
 * the recipient name are trimmed and a blank one counts as left out. So the length limits apply to the
 * trimmed text, and the same transfer typed a bit differently gives the same idempotency hash.
 *
 * @param recipientName required when the account belongs to someone else; ignored for own accounts
 * @param title         the default title for the transfer type is used when it is left out
 * @param currency      leave it out or send null; anything other than PLN is rejected
 */
public record TransferRequest(
        @NotNull UUID sourceAccountId,
        @Schema(example = "PL18 9990 0012 1000 0000 0000 0002") @NotBlank @ValidIban String targetIban,
        @Schema(example = "Jan Kowalski", nullable = true, maxLength = 140) @Size(max = 140) String recipientName,
        @Schema(example = "Dinner", nullable = true, maxLength = 140) @Size(max = 140) String title,
        @Schema(type = "string", example = "150.00")
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Schema(example = "PLN", nullable = true) String currency) {

    public TransferRequest {
        targetIban = IbanValidator.normalize(targetIban);
        recipientName = trimToNull(recipientName);
        title = trimToNull(title);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
