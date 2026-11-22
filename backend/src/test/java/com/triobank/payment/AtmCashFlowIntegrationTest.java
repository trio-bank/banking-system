package com.triobank.payment;

import static com.triobank.BankApi.SAVINGS;
import static com.triobank.BankApi.assertProblem;
import static com.triobank.BankApi.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.ApiTestBase;
import com.triobank.BankApi;
import com.triobank.BankApi.Customer;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

/** US-14 ATM deposit, US-15 ATM withdrawal. */
class AtmCashFlowIntegrationTest extends ApiTestBase {

    // ----------------------------------------------------------- TC-34 deposit

    @Test
    @Tag("TC-34")
    void TC34_deposit200_updatesBalanceAndBankCash_andWritesTwoLedgerEntries() throws Exception {
        Customer c = api.newCustomer();
        BigDecimal cashBefore = bankCashBalance();

        MvcResult result = api.deposit(c.token(), c.personalAccountId(), "200.00", newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CASH_DEPOSIT"))
                .andExpect(jsonPath("$.channel").value("ATM"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.sender.name").value("Trio Bank"))
                .andExpect(jsonPath("$.sender.iban").doesNotExist())
                .andExpect(jsonPath("$.recipient.iban").value(c.iban()))
                .andExpect(jsonPath("$.recipient.ownAccount").value(true))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        UUID transactionId = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(BankApi.dec(JsonPath.read(body, "$.amount"))).isEqualByComparingTo("200.00");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/transactions/" + transactionId);

        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("200.00");
        assertThat(bankCashBalance().subtract(cashBefore)).as("BANK_CASH").isEqualByComparingTo("-200.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE transaction_id = ?",
                Integer.class, transactionId)).isEqualTo(2);
    }

    @Test
    @Tag("TC-34")
    void TC34_deposit_boundaries10And20000_areAccepted() throws Exception {
        Customer c = api.newCustomer();

        api.deposit(c.token(), c.personalAccountId(), "10.00", newKey()).andExpect(status().isCreated());
        api.deposit(c.token(), c.personalAccountId(), "20000.00", newKey()).andExpect(status().isCreated());

        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("20010.00");
    }

    @Test
    @Tag("TC-34")
    void TC34_deposit_withoutIdempotencyKey_returns400() throws Exception {
        Customer c = api.newCustomer();

        assertProblem(api.deposit(c.token(), c.personalAccountId(), "200.00", null),
                400, "IDEMPOTENCY_KEY_MISSING");
    }

    @Test
    @Tag("TC-34")
    void TC34_deposit_toForeignAccount_returns404() throws Exception {
        Customer a = api.newCustomer();
        Customer b = api.newCustomer();

        assertProblem(api.deposit(b.token(), a.personalAccountId(), "100.00", newKey()),
                404, "RESOURCE_NOT_FOUND");
        assertThat(api.balance(a.token(), a.personalAccountId())).isEqualByComparingTo("0.00");
    }

    @Test
    @Tag("TC-34")
    void TC34_deposit_withoutToken_returns401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/atm/deposits")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"%s\",\"amount\":100.00}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Tag("TC-34")
    void TC34_deposit_toBlockedAccount_returns422() throws Exception {
        Customer c = api.newCustomer();
        setAccountStatus(c.personalAccountId(), "BLOCKED");

        assertProblem(api.deposit(c.token(), c.personalAccountId(), "100.00", newKey()),
                422, "RECIPIENT_ACCOUNT_UNAVAILABLE");
    }

    // ------------------------------------------------- TC-31 idempotency (ATM)

    @Test
    @Tag("TC-31")
    void TC31_sameKeySameRequest_returnsOriginalWith200_andMovesMoneyOnce() throws Exception {
        Customer c = api.newCustomer();
        String key = newKey();

        String first = BankApi.body(api.deposit(c.token(), c.personalAccountId(), "100.00", key)
                .andExpect(status().isCreated()));
        String second = BankApi.body(api.deposit(c.token(), c.personalAccountId(), "100.00", key)
                .andExpect(status().isOk()));

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("100.00");
        assertThat(countTransactionsOfUser(c.userId())).isEqualTo(1);
    }

    @Test
    @Tag("TC-31")
    void TC31_sameKeyDifferentAmount_returns409() throws Exception {
        Customer c = api.newCustomer();
        String key = newKey();
        api.deposit(c.token(), c.personalAccountId(), "100.00", key).andExpect(status().isCreated());

        assertProblem(api.deposit(c.token(), c.personalAccountId(), "200.00", key), 409, "IDEMPOTENCY_KEY_REUSED");
        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("100.00");
    }

    @Test
    @Tag("TC-31")
    void TC31_keyUsedForDeposit_cannotBeReusedForWithdrawal() throws Exception {
        Customer c = api.newCustomerWithBalance("500.00");
        String key = newKey();
        api.deposit(c.token(), c.personalAccountId(), "100.00", key).andExpect(status().isCreated());

        assertProblem(api.withdraw(c.token(), c.personalAccountId(), "100.00", key), 409, "IDEMPOTENCY_KEY_REUSED");
    }

    // --------------------------------------------------------- TC-35 limits

    @ParameterizedTest(name = "deposit and withdrawal of {0} are rejected")
    @ValueSource(strings = {"15", "5", "10.50", "25000", "20010"})
    @Tag("TC-35")
    void TC35_amountOutsideAtmRules_returns422(String amount) throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");

        assertProblem(api.deposit(c.token(), c.personalAccountId(), amount, newKey()), 422, "CASH_AMOUNT_INVALID");
        assertProblem(api.withdraw(c.token(), c.personalAccountId(), amount, newKey()), 422, "CASH_AMOUNT_INVALID");
        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("100.00");
    }

    @Test
    @Tag("TC-35")
    void TC35_withdrawalLimit_5000Accepted_5010Rejected() throws Exception {
        Customer c = api.newCustomerWithBalance("10000.00");

        assertProblem(api.withdraw(c.token(), c.personalAccountId(), "5010.00", newKey()),
                422, "CASH_AMOUNT_INVALID");
        api.withdraw(c.token(), c.personalAccountId(), "5000.00", newKey()).andExpect(status().isCreated());

        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("5000.00");
    }

    @ParameterizedTest(name = "amount {0} is a broken request, 400")
    @ValueSource(strings = {"0", "-10", "10.001"})
    @Tag("TC-35")
    void TC35_brokenAmount_returns400(String amount) throws Exception {
        Customer c = api.newCustomer();

        assertProblem(api.deposit(c.token(), c.personalAccountId(), amount, newKey()), 400, "VALIDATION_ERROR");
    }

    @Test
    @Tag("TC-35")
    void TC35_currencyOtherThanPln_returns422() throws Exception {
        Customer c = api.newCustomer();

        assertProblem(api.cash("deposits", c.token(), c.personalAccountId(), "100.00", "EUR", newKey()),
                422, "CURRENCY_NOT_SUPPORTED");
        api.cash("deposits", c.token(), c.personalAccountId(), "100.00", "PLN", newKey())
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------ TC-36 savings

    @Test
    @Tag("TC-36")
    void TC36_withdrawalFromSavings_returns422() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        UUID savings = api.openAccountId(c.token(), SAVINGS, null);

        assertProblem(api.withdraw(c.token(), savings, "10.00", newKey()),
                422, "OPERATION_NOT_ALLOWED_FOR_ACCOUNT");
    }

    @Test
    @Tag("TC-36")
    void TC36_depositToSavings_returns422() throws Exception {
        Customer c = api.newCustomer();
        UUID savings = api.openAccountId(c.token(), SAVINGS, null);

        assertProblem(api.deposit(c.token(), savings, "10.00", newKey()),
                422, "OPERATION_NOT_ALLOWED_FOR_ACCOUNT");
        assertThat(api.balance(c.token(), savings)).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------ TC-37 balance

    @Test
    @Tag("TC-37")
    void TC37_withdrawalMoreThanBalance_returns422_andStoresNothing() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        int transactionsBefore = countTransactionsOfUser(c.userId());

        assertProblem(api.withdraw(c.token(), c.personalAccountId(), "110.00", newKey()),
                422, "INSUFFICIENT_FUNDS");

        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("100.00");
        assertThat(countTransactionsOfUser(c.userId())).isEqualTo(transactionsBefore);
    }

    @Test
    @Tag("TC-37")
    void TC37_withdrawalOfWholeBalance_leavesZero() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        BigDecimal cashBefore = bankCashBalance();

        api.withdraw(c.token(), c.personalAccountId(), "100.00", newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CASH_WITHDRAWAL"))
                .andExpect(jsonPath("$.channel").value("ATM"))
                .andExpect(jsonPath("$.sender.ownAccount").value(true))
                .andExpect(jsonPath("$.recipient.iban").doesNotExist());

        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("0.00");
        assertThat(bankCashBalance().subtract(cashBefore)).as("BANK_CASH").isEqualByComparingTo("100.00");
    }

    @Test
    @Tag("TC-37")
    void TC37_withdrawalFromBlockedAccount_returns422() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        setAccountStatus(c.personalAccountId(), "BLOCKED");

        assertProblem(api.withdraw(c.token(), c.personalAccountId(), "10.00", newKey()),
                422, "SOURCE_ACCOUNT_UNAVAILABLE");
    }

}
