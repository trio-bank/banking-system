package com.triobank.common.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bank-wide settings from the {@code bank.*} properties.
 *
 * @param sortCode 8-digit sort code used in our IBANs
 * @param currency the only currency we work with for now
 * @param timeZone time zone of the bank, e.g. for transaction references and interest days
 */
@Validated
@ConfigurationProperties("bank")
public record BankProperties(
        @NotNull @Pattern(regexp = "\\d{8}") String sortCode,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull ZoneId timeZone) {
}
