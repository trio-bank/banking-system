package com.triobank.customer.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerMapperTest {

    @Test
    void peselShowsOnlyLastFourDigits() {
        assertThat(CustomerMapper.maskPesel("90051412343")).isEqualTo("*******2343");
    }

}
