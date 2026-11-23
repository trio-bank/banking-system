package com.triobank.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One side of a transaction as the caller sees it.
 *
 * @param iban       null for the bank's own accounts
 * @param ownAccount whether the account belongs to the caller
 */
public record PartyDto(
        @Schema(example = "Anna Nowak") String name,
        @Schema(example = "PL45999000121000000000000001", nullable = true) String iban,
        boolean ownAccount) {
}
