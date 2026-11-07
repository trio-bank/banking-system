package com.triobank.common.pesel;

import static org.assertj.core.api.Assertions.assertThat;

import com.triobank.common.pesel.PeselValidator.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PeselValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"90051412343", "85110356782", "01222843219", "10270111119"})
    void acceptsValidPesels(String pesel) {
        assertThat(PeselValidator.check(pesel)).isEqualTo(Result.VALID);
    }

    @Test
    void TC03_rejectsWrongCheckDigit() {
        assertThat(PeselValidator.check("90051412344")).isEqualTo(Result.INVALID_CHECKSUM);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"9005141234", "900514123430", "9005141234a", "900514 2343"})
    void rejectsWrongFormat(String pesel) {
        assertThat(PeselValidator.check(pesel)).isEqualTo(Result.INVALID_FORMAT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "90130100004",  // month 13
            "90023100007",  // 31 February
            "90000100008"   // month 0
    })
    void rejectsPeselWithoutRealDate(String pesel) {
        assertThat(PeselValidator.check(pesel)).isEqualTo(Result.INVALID_DATE);
    }

    @Test
    void isValidMatchesCheck() {
        assertThat(PeselValidator.isValid("90051412343")).isTrue();
        assertThat(PeselValidator.isValid("90051412344")).isFalse();
    }

}
