package com.triobank.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "anna.nowak@example.com")
        @NotBlank @Size(max = 254) String email,

        @Schema(example = "Str0ng!Passw0rd")
        @NotBlank @Size(max = 128) String password) {

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }

}
