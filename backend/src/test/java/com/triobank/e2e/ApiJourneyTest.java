package com.triobank.e2e;

import static com.triobank.BankApi.SAVINGS;
import static com.triobank.BankApi.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.ApiTestBase;
import com.triobank.BankApi;
import com.triobank.TestDataFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * User journeys through the API only (no browser): the whole stack, real database.
 * Run alone in CI with {@code -Dgroups=e2e} (.github/workflows/e2e.yml).
 */
@Tag("e2e")
class ApiJourneyTest extends ApiTestBase {

    /** Journey 1 (TC-01, TC-07): register, log in, see the personal account with a valid IBAN. */
    @Test
    @Tag("TC-01")
    @Tag("TC-07")
    void journey1_register_login_seePersonalAccountWithIban() throws Exception {
        TestDataFactory.RegisteredCustomer registered = data.registerCustomer();
        assertThat(TestDataFactory.isValidPolishIban(registered.iban())).isTrue();

        String token = data.loginAndGetToken(registered.email(), registered.password());

        String body = BankApi.body(api.accounts(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PERSONAL"))
                .andExpect(jsonPath("$[0].iban").value(registered.iban()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE")));
        assertThat(BankApi.dec(JsonPath.read(body, "$[0].balance"))).isEqualByComparingTo("0.00");

        api.me(token).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(registered.email()));
    }

    /**
     * Journey 2 (TC-34, TC-15, TC-32): ATM deposit, open savings, history.
     * The own transfer step from docs/09 §5.7 comes in sprint 3 (US-10); add it here then.
     */
    @Test
    @Tag("TC-34")
    @Tag("TC-15")
    @Tag("TC-32")
    void journey2_atmDeposit_openSavings_history() throws Exception {
        BankApi.Customer c = api.newCustomer();

        api.deposit(c.token(), c.personalAccountId(), "500.00", newKey()).andExpect(status().isCreated());
        UUID savings = api.openAccountId(c.token(), SAVINGS, "Holiday savings");

        String accounts = BankApi.body(api.accounts(c.token()).andExpect(status().isOk()));
        List<String> names = JsonPath.read(accounts, "$[*].name");
        assertThat(names).hasSize(2).contains("Holiday savings");
        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("500.00");
        assertThat(api.balance(c.token(), savings)).isEqualByComparingTo("0.00");

        api.history(c.token(), c.personalAccountId(), 0, 20)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("CASH_DEPOSIT"))
                .andExpect(jsonPath("$.content[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value("500.00"));
        api.history(c.token(), savings, 0, 20)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

}
