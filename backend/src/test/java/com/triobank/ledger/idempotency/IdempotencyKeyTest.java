package com.triobank.ledger.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeyTest {

    @Test
    void uuidIsAccepted() {
        assertThat(IdempotencyKey.require("3f2b8c1e-9a4d-4e7f-8b21-5c6d7e8f9a0b"))
                .isEqualTo("3f2b8c1e-9a4d-4e7f-8b21-5c6d7e8f9a0b");
    }

    @Test
    void upperCaseUuidIsTheSameKey() {
        assertThat(IdempotencyKey.require(" 3F2B8C1E-9A4D-4E7F-8B21-5C6D7E8F9A0B "))
                .isEqualTo("3f2b8c1e-9a4d-4e7f-8b21-5c6d7e8f9a0b");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-a-uuid", "1-1-1-1-1", "3f2b8c1e9a4d4e7f8b215c6d7e8f9a0b",
            "3f2b8c1e-9a4d-4e7f-8b21-5c6d7e8f9a0b-extra"})
    void missingOrInvalidKeyIsRejected(String header) {
        assertThatThrownBy(() -> IdempotencyKey.require(header))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_MISSING));
    }

}
