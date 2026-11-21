package com.triobank.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param name trimmed here, before validation, so the length limit applies to the trimmed name
 */
public record RenameAccountRequest(
        @Schema(example = "Daily expenses", minLength = 1, maxLength = 50) @NotBlank @Size(max = 50) String name) {

    public RenameAccountRequest {
        name = name == null ? null : name.trim();
    }

}
