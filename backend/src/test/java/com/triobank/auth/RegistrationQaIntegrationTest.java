package com.triobank.auth;

import static com.triobank.TestDataFactory.PASSWORD;
import static com.triobank.TestDataFactory.SORT_CODE;
import static com.triobank.TestDataFactory.fieldCodes;
import static com.triobank.TestDataFactory.isValidPolishIban;
import static com.triobank.TestDataFactory.registerJson;
import static com.triobank.TestDataFactory.registerRequest;
import static com.triobank.TestDataFactory.uniqueEmail;
import static com.triobank.TestDataFactory.uniquePesel;
import static com.triobank.TestDataFactory.underagePesel;
import static com.triobank.TestDataFactory.withBrokenCheckDigit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.AbstractIntegrationTest;
import com.triobank.TestDataFactory.RegisteredCustomer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** US-01 registration: TC-01...TC-06 from docs/09-testing.md §5.1. */
class RegistrationQaIntegrationTest extends AbstractIntegrationTest {

    @Test
    void TC01_register_validData_createsCustomerAndPersonalAccount() throws Exception {
        String email = uniqueEmail();
        String pesel = uniquePesel();

        MvcResult result = mockMvc.perform(registerRequest(registerJson(email, PASSWORD, pesel, "")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/me"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.account.type").value("PERSONAL"))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.account.currency").value("PLN"))
                .andExpect(jsonPath("$.account.balance").value("0.00"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        UUID userId = UUID.fromString(JsonPath.read(body, "$.userId"));
        UUID accountId = UUID.fromString(JsonPath.read(body, "$.account.id"));
        String customerNumber = JsonPath.read(body, "$.customerNumber");
        String iban = JsonPath.read(body, "$.account.iban");

        assertThat(customerNumber).matches("\\d{8}");
        assertThat(isValidPolishIban(iban)).isTrue();
        assertThat(iban.substring(4, 12)).isEqualTo(SORT_CODE);

        // what is stored
        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, userId))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select role from users where id = ?", String.class, userId))
                .isEqualTo("CUSTOMER");
        assertThat(jdbc.queryForObject("select customer_number from customers where user_id = ?",
                String.class, userId)).isEqualTo(customerNumber);
        assertThat(jdbc.queryForObject("select date_of_birth from customers where user_id = ?",
                LocalDate.class, userId)).isEqualTo(LocalDate.of(1990, 5, 14));
        assertThat(count("select count(*) from accounts a join customers c on c.id = a.customer_id "
                + "where c.user_id = ?", userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select balance from accounts where iban = ?", BigDecimal.class, iban))
                .isEqualByComparingTo(BigDecimal.ZERO);

        // audit events (US-01 AC)
        assertThat(count("select count(*) from audit_events where action = 'USER_REGISTERED' "
                + "and actor_user_id = ?", userId)).isEqualTo(1);
        assertThat(count("select count(*) from audit_events where action = 'ACCOUNT_OPENED' "
                + "and (actor_user_id = ? or target_id = ?)", userId, accountId)).isEqualTo(1);
    }

    @Test
    void TC02_register_emailAlreadyRegisteredInOtherCase_returns409() throws Exception {
        RegisteredCustomer existing = data.registerCustomer();

        mockMvc.perform(registerRequest(
                        registerJson(existing.email().toUpperCase(), PASSWORD, uniquePesel(), "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    /** Second half of the US-01 acceptance criterion "email or PESEL that is already registered". */
    @Test
    void US01_register_peselAlreadyRegistered_returns409() throws Exception {
        RegisteredCustomer existing = data.registerCustomer();

        mockMvc.perform(registerRequest(registerJson(uniqueEmail(), PASSWORD, existing.pesel(), "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PESEL_ALREADY_REGISTERED"));
    }

    @Test
    void TC03_register_peselWithInvalidCheckDigit_returns400() throws Exception {
        String broken = withBrokenCheckDigit(uniquePesel());

        MvcResult result = mockMvc.perform(registerRequest(registerJson(uniqueEmail(), PASSWORD, broken, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(fieldCodes(result, "pesel")).contains("INVALID_CHECKSUM");
    }

    @Test
    void TC04_register_underagePesel_returns422() throws Exception {
        mockMvc.perform(registerRequest(registerJson(uniqueEmail(), PASSWORD, underagePesel(), "")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CUSTOMER_UNDERAGE"));
    }

    @Test
    void TC05_register_passwordWithoutSpecialCharacter_returns400() throws Exception {
        MvcResult result = mockMvc.perform(
                        registerRequest(registerJson(uniqueEmail(), "NoSpecialChar123", uniquePesel(), "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(fieldCodes(result, "password")).contains("WEAK_PASSWORD");
    }

    @Test
    void TC06_register_roleInRequestIsIgnored_userIsCustomer() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(registerRequest(registerJson(email, PASSWORD, uniquePesel(), "\"role\": \"ADMIN\"")))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select role from users where email = ?", String.class, email))
                .isEqualTo("CUSTOMER");
    }

}
