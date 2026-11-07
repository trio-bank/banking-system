package com.triobank.common.pesel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class PeselParserTest {

    @ParameterizedTest
    @CsvSource({
            "90051412343, 1990-05-14",
            "85110356782, 1985-11-03",
            "01222843219, 2001-02-28",
            "10270111119, 2010-07-01"
    })
    void readsBirthDateOfTestPesels(String pesel, LocalDate expected) {
        assertThat(PeselParser.birthDate(pesel)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "99811500000, 1899-01-15",
            "99121500000, 1999-12-15",
            "00211500000, 2000-01-15",
            "00411500000, 2100-01-15",
            "00611500000, 2200-01-15"
    })
    void usesMonthOffsetForCentury(String pesel, LocalDate expected) {
        assertThat(PeselParser.birthDate(pesel)).isEqualTo(expected);
    }

    @Test
    void knowsLeapYears() {
        assertThat(PeselParser.birthDate("00222900000")).isEqualTo(LocalDate.of(2000, 2, 29));
        assertThatThrownBy(() -> PeselParser.birthDate("01222900000")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsWhenThereIsNoRealDate() {
        assertThatThrownBy(() -> PeselParser.birthDate("90130100004")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PeselParser.birthDate("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PeselParser.birthDate(null)).isInstanceOf(IllegalArgumentException.class);
    }

}
