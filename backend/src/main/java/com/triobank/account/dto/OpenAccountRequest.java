package com.triobank.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param name trimmed here, before validation; blank becomes {@code null} and the product name is used instead
 */
public record OpenAccountRequest(
        @Schema(example = "SAVINGS_STANDARD") @NotBlank String productCode,
        @Schema(example = "Holiday savings", nullable = true, maxLength = 50) @Size(max = 50) String name) {

    public OpenAccountRequest {
        name = name == null || name.isBlank() ? null : name.trim();
    }

}
