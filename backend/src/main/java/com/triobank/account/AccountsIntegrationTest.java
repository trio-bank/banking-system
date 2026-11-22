package com.triobank.account;

import static com.triobank.BankApi.PERSONAL;
import static com.triobank.BankApi.SAVINGS;
import static com.triobank.BankApi.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.ApiTestBase;
import com.triobank.BankApi;
import com.triobank.BankApi.Customer;
import com.triobank.TestDataFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** US-06 list, US-07 open, US-08 details, US-09 rename. */
class AccountsIntegrationTest extends ApiTestBase {

    // ------------------------------------------------------- TC-15…17 open

    @Test
    @Tag("TC-15")
    void TC15_openSavings_withActivePersonal_createsAccountWithDefaultName() throws Exception {
        Customer c = api.newCustomer();

        MvcResult result = api.open(c.token(), SAVINGS, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SAVINGS"))
                .andExpect(jsonPath("$.productCode").value(SAVINGS))
                .andExpect(jsonPath("$.name").value("Trio Savings Account"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String iban = JsonPath.read(body, "$.iban");
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/accounts/" + id);
        assertThat(TestDataFactory.isValidPolishIban(iban)).as("IBAN checksum").isTrue();
        assertThat(iban).as("IBAN unique").isNotEqualTo(c.iban());
        assertThat(BankApi.dec(JsonPath.read(body, "$.balance"))).isEqualByComparingTo("0.00");
    }

    @Test
    @Tag("TC-15")
    void TC15_openSavings_withCustomName_usesIt() throws Exception {
        Customer c = api.newCustomer();

        api.open(c.token(), SAVINGS, "Holiday savings")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Holiday savings"));
    }

    @Test
    @Tag("TC-15")
    void TC15_openAccount_unknownProduct_returns404() throws Exception {
        Customer c = api.newCustomer();

        assertProblem(api.open(c.token(), "NO_SUCH_PRODUCT", null), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    @Tag("TC-16")
    void TC16_thirdPersonalAccount_isRejected_blockedCountsClosedDoesNot() throws Exception {
        Customer c = api.newCustomer();
        UUID second = api.openAccountId(c.token(), PERSONAL, "Second");

        assertProblem(api.open(c.token(), PERSONAL, null), 422, "ACCOUNT_LIMIT_REACHED");

        setAccountStatus(second, "BLOCKED");
        assertProblem(api.open(c.token(), PERSONAL, null), 422, "ACCOUNT_LIMIT_REACHED");

        setAccountStatus(second, "CLOSED");
        api.open(c.token(), PERSONAL, "Third").andExpect(status().isCreated());
    }

    @Test
    @Tag("TC-16")
    void TC16_thirdSavingsAccount_isRejected() throws Exception {
        Customer c = api.newCustomer();
        api.openAccountId(c.token(), SAVINGS, null);
        api.openAccountId(c.token(), SAVINGS, null);

        assertProblem(api.open(c.token(), SAVINGS, null), 422, "ACCOUNT_LIMIT_REACHED");
    }

    @Test
    @Tag("TC-17")
    void TC17_openSavings_whenPersonalIsBlocked_returnsPrerequisiteRequired() throws Exception {
        Customer c = api.newCustomer();
        setAccountStatus(c.personalAccountId(), "BLOCKED");

        assertProblem(api.open(c.token(), SAVINGS, null), 422, "PREREQUISITE_ACCOUNT_REQUIRED");

        String accounts = BankApi.body(api.accounts(c.token()).andExpect(status().isOk()));
        assertThat((List<?>) JsonPath.read(accounts, "$")).as("no savings account created").hasSize(1);
    }

    // ------------------------------------------------------- TC-18 list/details

    @Test
    @Tag("TC-18")
    void TC18_list_returnsOnlyOwnAccounts_personalFirst() throws Exception {
        Customer a = api.newCustomer();
        Customer b = api.newCustomer();
        UUID savings = api.openAccountId(a.token(), SAVINGS, null);

        String bodyA = BankApi.body(api.accounts(a.token()).andExpect(status().isOk()));
        List<String> idsA = JsonPath.read(bodyA, "$[*].id");
        assertThat(idsA).containsExactly(a.personalAccountId().toString(), savings.toString());

        String bodyB = BankApi.body(api.accounts(b.token()).andExpect(status().isOk()));
        List<String> idsB = JsonPath.read(bodyB, "$[*].id");
        assertThat(idsB).containsExactly(b.personalAccountId().toString());
    }

    @Test
    @Tag("TC-18")
    void TC18_details_savingsShowRateAndAccruedInterest_personalHasNoAccrual() throws Exception {
        Customer c = api.newCustomer();
        UUID savings = api.openAccountId(c.token(), SAVINGS, null);

        String savingsBody = BankApi.body(api.account(c.token(), savings).andExpect(status().isOk()));
        assertThat(BankApi.dec(JsonPath.read(savingsBody, "$.interestRate"))).isEqualByComparingTo("4");
        assertThat(BankApi.dec(JsonPath.read(savingsBody, "$.accruedInterest"))).isEqualByComparingTo("0");

        api.account(c.token(), c.personalAccountId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(c.iban()))
                .andExpect(jsonPath("$.accruedInterest").doesNotExist());
    }

    @Test
    @Tag("TC-18")
    void TC18_details_ofForeignOrUnknownAccount_looksTheSame_404() throws Exception {
        Customer a = api.newCustomer();
        Customer b = api.newCustomer();

        assertProblem(api.account(b.token(), a.personalAccountId()), 404, "RESOURCE_NOT_FOUND");
        assertProblem(api.account(b.token(), UUID.randomUUID()), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    @Tag("TC-18")
    void TC18_list_withoutToken_returns401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ------------------------------------------------------------ TC-19 rename

    @Test
    @Tag("TC-19")
    void TC19_rename_validName_isTrimmedAndSaved() throws Exception {
        Customer c = api.newCustomer();

        api.rename(c.token(), c.personalAccountId(), "  Daily expenses  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Daily expenses"));

        api.account(c.token(), c.personalAccountId())
                .andExpect(jsonPath("$.name").value("Daily expenses"));
    }

    @Test
    @Tag("TC-19")
    void TC19_rename_sameName_returns200() throws Exception {
        Customer c = api.newCustomer();
        api.rename(c.token(), c.personalAccountId(), "Same").andExpect(status().isOk());

        api.rename(c.token(), c.personalAccountId(), "Same")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Same"));
    }

    @Test
    @Tag("TC-19")
    void TC19_rename_51Characters_returns400WithFieldError() throws Exception {
        Customer c = api.newCustomer();

        MvcResult result = api.rename(c.token(), c.personalAccountId(), "x".repeat(51))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(TestDataFactory.fieldCodes(result, "name")).isNotEmpty();
    }

    @Test
    @Tag("TC-19")
    void TC19_rename_blankName_returns400() throws Exception {
        Customer c = api.newCustomer();

        assertProblem(api.rename(c.token(), c.personalAccountId(), "   "), 400, "VALIDATION_ERROR");
    }

    @Test
    @Tag("TC-19")
    void TC19_rename_blockedAccount_returns422() throws Exception {
        Customer c = api.newCustomer();
        setAccountStatus(c.personalAccountId(), "BLOCKED");

        assertProblem(api.rename(c.token(), c.personalAccountId(), "New name"),
                422, "OPERATION_NOT_ALLOWED_FOR_ACCOUNT");
    }

    @Test
    @Tag("TC-19")
    void TC19_rename_foreignAccount_returns404() throws Exception {
        Customer a = api.newCustomer();
        Customer b = api.newCustomer();

        assertProblem(api.rename(b.token(), a.personalAccountId(), "Mine now"), 404, "RESOURCE_NOT_FOUND");
    }

}
