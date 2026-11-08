package com.triobank.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Personal data the customer sees about themselves. Only the last 4 digits of the PESEL are shown.
 */
public record CustomerProfileDto(
        UUID id,
        @Schema(example = "10000001") String customerNumber,
        @Schema(example = "Anna") String firstName,
        @Schema(example = "Nowak") String lastName,
        @Schema(example = "*******2343") String peselMasked,
        LocalDate dateOfBirth,
        @Schema(example = "anna.nowak@example.com") String email,
        @Schema(example = "+48501234567") String phoneNumber,
        AddressDto address,
        Instant createdAt) {
}
