package com.triobank.auth.dto;

import com.triobank.account.dto.AccountDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record RegistrationResponse(
        UUID userId,
        @Schema(example = "10000001") String customerNumber,
        @Schema(example = "anna.nowak@example.com") String email,
        AccountDto account) {
}
