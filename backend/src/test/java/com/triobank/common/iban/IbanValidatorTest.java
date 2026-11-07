package com.triobank.common.iban;

import static org.assertj.core.api.Assertions.assertThat;

import com.triobank.common.iban.IbanValidator.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class IbanValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "PL45999000121000000000000001",
            "PL18999000121000000000000002",
            "PL25999000129000000000000001",
            "PL41999000129000000000000004"
    })
    void acceptsValidIbans(String iban) {
        assertThat(IbanValidator.check(iban)).isEqualTo(Result.VALID);
    }

    @Test
    void rejectsWrongCheckDigits() {
        assertThat(IbanValidator.check("PL45999000121000000000000002")).isEqualTo(Result.INVALID_CHECKSUM);
    }

    @Test
    void acceptsIbanOfAnotherBank() {
        // Real-looking IBAN with a different sort code: still a valid Polish IBAN.
        assertThat(IbanValidator.check("PL61109010140000071219812874")).isEqualTo(Result.VALID);
    }

    @Test
    void acceptsSpacesAndLowerCase() {
        assertThat(IbanValidator.check("pl45 9990 0012 1000 0000 0000 0001")).isEqualTo(Result.VALID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "PL4599900012100000000000000",      // 25 digits
            "PL459990001210000000000000011",    // 27 digits
            "DE45999000121000000000000001",     // not Polish
            "PL4599900012100000000000000A",
            "PL45-9990-0012-1000-0000-0000-0001"
    })
    void rejectsWrongFormat(String iban) {
        assertThat(IbanValidator.check(iban)).isEqualTo(Result.INVALID_FORMAT);
    }

    @Test
    void normalizeRemovesSpacesAndUpperCases() {
        assertThat(IbanValidator.normalize(" pl45 9990\t0012 1000 0000 0000 0001 "))
                .isEqualTo("PL45999000121000000000000001");
        assertThat(IbanValidator.normalize(null)).isNull();
    }

    @Test
    void isValidMatchesCheck() {
        assertThat(IbanValidator.isValid("PL45999000121000000000000001")).isTrue();
        assertThat(IbanValidator.isValid("PL45999000121000000000000002")).isFalse();
    }

}
