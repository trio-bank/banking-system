package com.triobank.ledger.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RequestHashTest {

    private static final String ACCOUNT = "7d1e4c22-5b3a-4a8e-b0f1-93c2d4e5f601";

    @Test
    void sameRequestGivesSameHash() {
        assertThat(RequestHash.of("ATM_DEPOSIT", ACCOUNT, new BigDecimal("200.00")))
                .isEqualTo(RequestHash.of("ATM_DEPOSIT", ACCOUNT, new BigDecimal("200.00")))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void amountScaleDoesNotMatter() {
        assertThat(RequestHash.of("ATM_DEPOSIT", ACCOUNT, new BigDecimal("200")))
                .isEqualTo(RequestHash.of("ATM_DEPOSIT", ACCOUNT, new BigDecimal("200.00")));
    }

    @Test
    void differentAmountGivesDifferentHash() {
        assertThat(RequestHash.of("ATM_DEPOSIT", ACCOUNT, new BigDecimal("200.00")))
                .isNotEqualTo(RequestHash.of("ATM_DEPOSIT", ACCOUNT, new BigDecimal("200.01")));
    }

    @Test
    void sameBodyForAnotherOperationGivesDifferentHash() {
        assertThat(RequestHash.of("ATM_DEPOSIT", ACCOUNT, new BigDecimal("200.00")))
                .isNotEqualTo(RequestHash.of("ATM_WITHDRAWAL", ACCOUNT, new BigDecimal("200.00")));
    }

    @Test
    void fieldBoundariesMatter() {
        assertThat(RequestHash.of("TRANSFER", "ab", "c")).isNotEqualTo(RequestHash.of("TRANSFER", "a", "bc"));
        assertThat(RequestHash.of("TRANSFER", "a;", "b")).isNotEqualTo(RequestHash.of("TRANSFER", "a", ";b"));
    }

    @Test
    void nullIsNotTheSameAsTextNullOrEmpty() {
        String withNull = RequestHash.of("ATM_DEPOSIT", ACCOUNT, null);

        assertThat(withNull)
                .isNotEqualTo(RequestHash.of("ATM_DEPOSIT", ACCOUNT, "null"))
                .isNotEqualTo(RequestHash.of("ATM_DEPOSIT", ACCOUNT, ""))
                .isEqualTo(RequestHash.of("ATM_DEPOSIT", ACCOUNT, null));
    }

}
