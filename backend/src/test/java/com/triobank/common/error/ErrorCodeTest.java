package com.triobank.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ErrorCodeTest {

    @Test
    void containsEveryCodeOfTheApiContract() {
        assertThat(ErrorCode.values()).hasSize(31);
    }

    @Test
    void typeIsDerivedFromName() {
        assertThat(ErrorCode.INSUFFICIENT_FUNDS.type())
                .hasToString("https://triobank.example/errors/insufficient-funds");
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::type).distinct()).hasSize(31);
    }

    @Test
    void statusesFollowTheContract() {
        assertThat(ErrorCode.VALIDATION_ERROR.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.USER_LOCKED.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.CONCURRENT_MODIFICATION.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INSUFFICIENT_FUNDS.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.FEATURE_DISABLED.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.INTERNAL_ERROR.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void apiExceptionDefaultsDetailToTitleAndCopiesProperties() {
        ApiException plain = new ApiException(ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(plain.getMessage()).isEqualTo("Insufficient funds");
        assertThat(plain.getProperties()).isEmpty();

        ApiException locked = new ApiException(ErrorCode.USER_LOCKED, "Locked", Map.of("lockedUntil", "x"));
        assertThat(locked.getCode()).isEqualTo(ErrorCode.USER_LOCKED);
        assertThat(locked.getProperties()).containsEntry("lockedUntil", "x");

        assertThatNullPointerException().isThrownBy(() -> new ApiException(null));
    }

}
