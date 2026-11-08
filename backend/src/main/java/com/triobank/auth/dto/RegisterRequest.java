package com.triobank.auth.dto;

import com.triobank.auth.PasswordWithoutEmail;
import com.triobank.auth.StrongPassword;
import com.triobank.common.pesel.ValidPesel;
import com.triobank.customer.dto.AddressDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * There is no role field on purpose: whoever registers here is a customer.
 */
@PasswordWithoutEmail
public record RegisterRequest(
        @Schema(example = "anna.nowak@example.com", description = "Case-insensitive, stored in lower case")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(example = "Str0ng!Passw0rd",
                description = "12–128 characters, lowercase, uppercase, digit, special character; "
                        + "must not contain the part of the email before @")
        @NotBlank @StrongPassword String password,

        @Schema(example = "Anna")
        @NotBlank @Size(max = 100) @Pattern(regexp = NAME) String firstName,

        @Schema(example = "Nowak")
        @NotBlank @Size(max = 100) @Pattern(regexp = NAME) String lastName,

        @Schema(example = "90051412343", description = "Date of birth is read from it")
        @NotBlank @ValidPesel String pesel,

        @Schema(example = "+48501234567", description = "E.164")
        @NotBlank @Pattern(regexp = "\\+[1-9]\\d{6,14}") String phoneNumber,

        @NotNull @Valid AddressDto address) {

    /** Letters, spaces, hyphens and apostrophes, e.g. "Anna-Maria" or "O'Brien". */
    static final String NAME = "\\p{L}[\\p{L} '\\-]*";

    /** Keeps the password and PESEL out of logs. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + "]";
    }

}
