package com.triobank.common.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyUtilsTest {

    @Test
    void parsesToScaleTwo() {
        assertThat(MoneyUtils.of("10")).hasToString("10.00");
        assertThat(MoneyUtils.of("1250.5")).hasToString("1250.50");
    }

    @Test
    void normalizesWithBankersRounding() {
        assertThat(MoneyUtils.normalize(new BigDecimal("0.125"))).hasToString("0.12");
        assertThat(MoneyUtils.normalize(new BigDecimal("0.135"))).hasToString("0.14");
        assertThat(MoneyUtils.normalize(null)).isNull();
    }

}
