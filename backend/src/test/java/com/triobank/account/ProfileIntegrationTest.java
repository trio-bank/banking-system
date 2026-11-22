package com.triobank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.ApiTestBase;
import com.triobank.BankApi;
import com.triobank.BankApi.Customer;
import com.triobank.TestDataFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** US-05 View profile: GET /api/v1/me, the {@code customer} object. */
class ProfileIntegrationTest extends ApiTestBase {

    @Test
    @Tag("TC-14")
    void TC14_profile_returnsAllFieldsWithMaskedPesel() throws Exception {
        Customer customer = api.newCustomer();

        String body = BankApi.body(api.me(customer.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(customer.email()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.customer.email").value(customer.email()))
                .andExpect(jsonPath("$.customer.firstName").value(TestDataFactory.FIRST_NAME))
                .andExpect(jsonPath("$.customer.lastName").value(TestDataFactory.LAST_NAME))
                .andExpect(jsonPath("$.customer.dateOfBirth").value(TestDataFactory.ADULT_BIRTH_DATE.toString()))
                .andExpect(jsonPath("$.customer.phoneNumber").value("+48501234567"))
                .andExpect(jsonPath("$.customer.customerNumber").isNotEmpty())
                .andExpect(jsonPath("$.customer.address").isNotEmpty())
                .andExpect(jsonPath("$.customer.createdAt").isNotEmpty()));

        String masked = JsonPath.read(body, "$.customer.peselMasked");
        assertThat(masked).matches("\\*{7}\\d{4}");
        assertThat(masked).endsWith(customer.pesel().substring(7));
        assertThat(body).as("full PESEL must not appear anywhere").doesNotContain(customer.pesel());
        assertThat((String) JsonPath.read(body, "$.customer.customerNumber")).matches("\\d{8}");
    }

    @Test
    @Tag("TC-14")
    void TC14_profile_withoutToken_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
 
