package com.triobank.common.iban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triobank.common.config.BankProperties;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class IbanGeneratorTest {

    private final IbanGenerator generator = new IbanGenerator(bank("99900012"));

    @ParameterizedTest
    @CsvSource({
            "1000000000000001, PL45999000121000000000000001",
            "1000000000000002, PL18999000121000000000000002",
            "9000000000000001, PL25999000129000000000000001",
            "9000000000000004, PL41999000129000000000000004"
    })
    void generatesKnownIbans(String accountNumber, String expected) {
        assertThat(generator.generate(accountNumber)).isEqualTo(expected);
    }

    @Test
    void generatedIbansAreValid() {
        for (long number = 1000000000000001L; number < 1000000000000300L; number++) {
            assertThat(IbanValidator.isValid(generator.generate(Long.toString(number)))).isTrue();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"100000000000001", "10000000000000011", "100000000000000A"})
    void rejectsAccountNumberThatIsNot16Digits(String accountNumber) {
        assertThatThrownBy(() -> generator.generate(accountNumber)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checksSortCodeCheckDigit() {
        assertThat(IbanGenerator.isValidSortCode("99900012")).isTrue();
        assertThat(IbanGenerator.isValidSortCode("10901014")).isTrue();
        assertThat(IbanGenerator.isValidSortCode("99900013")).isFalse();
        assertThat(IbanGenerator.isValidSortCode("9990001")).isFalse();
        assertThat(IbanGenerator.isValidSortCode(null)).isFalse();
    }

    @Test
    void refusesToStartWithWrongSortCode() {
        assertThatThrownBy(() -> new IbanGenerator(bank("99900013")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("99900013");
    }

    @Test
    void readsSortCodeFromBankProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues("bank.sort-code=99900012", "bank.currency=PLN", "bank.time-zone=Europe/Warsaw")
                .run(context -> {
                    assertThat(context.getBean(IbanGenerator.class).generate("1000000000000001"))
                            .isEqualTo("PL45999000121000000000000001");
                    assertThat(context.getBean(BankProperties.class).timeZone()).isEqualTo(ZoneId.of("Europe/Warsaw"));
                });
    }

    @Test
    void appFailsToStartWithBadSortCode() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues("bank.sort-code=99900013", "bank.currency=PLN", "bank.time-zone=Europe/Warsaw")
                .run(context -> assertThat(context).hasFailed());
    }

    private static BankProperties bank(String sortCode) {
        return new BankProperties(sortCode, "PLN", ZoneId.of("Europe/Warsaw"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BankProperties.class)
    @Import(IbanGenerator.class)
    static class TestConfig {
    }

}
