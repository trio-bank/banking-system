package com.triobank.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.AbstractIntegrationTest;
import com.triobank.TestDataFactory;
import com.triobank.TestDataFactory.RegisteredCustomer;
import com.triobank.ledger.LedgerInvariants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * POST /api/v1/atm/withdrawals against a real database. Every test starts with 100.00 on the account.
 */
class AtmWithdrawalIntegrationTest extends AbstractIntegrationTest {

    private static final String DEPOSITS = "/api/v1/atm/deposits";
    private static final String WITHDRAWALS = "/api/v1/atm/withdrawals";
    private static final UUID BANK_CASH = UUID.fromString("00000000-0000-4000-8000-000000000201");

    private String annaBearer;
    private String annaIban;
    private UUID annaAccount;

    @BeforeEach
    void customerWith100() throws Exception {
        RegisteredCustomer anna = data.registerCustomer();
        annaBearer = TestDataFactory.bearer(data.loginAndGetToken(anna.email(), anna.password()));
        annaIban = anna.iban();
        annaAccount = jdbc.queryForObject("SELECT id FROM accounts WHERE iban = ?", UUID.class, annaIban);
        call(DEPOSITS, newKey(), body(annaAccount, "100.00")).andExpect(status().isCreated());
    }

    @AfterEach
    void ledgerStaysBalanced() {
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    @Test
    void withdrawalMovesCashFromAccountToBank() throws Exception {
        BigDecimal cashBefore = balance(BANK_CASH);

        MvcResult result = withdraw(newKey(), body(annaAccount, "50.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CASH_WITHDRAWAL"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.channel").value("ATM"))
                .andExpect(jsonPath("$.amount").value("50.00"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.title").value("ATM cash withdrawal"))
                .andExpect(jsonPath("$.sender.name").value("Anna Nowak"))
                .andExpect(jsonPath("$.sender.iban").value(annaIban))
                .andExpect(jsonPath("$.sender.ownAccount").value(true))
                .andExpect(jsonPath("$.recipient.name").value("Trio Bank"))
                .andExpect(jsonPath("$.recipient.iban").value((Object) null))
                .andExpect(jsonPath("$.recipient.ownAccount").value(false))
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/transactions/" + id);
        assertThat(balance(annaAccount)).isEqualByComparingTo("50.00");
        assertThat(balance(BANK_CASH)).isEqualByComparingTo(cashBefore.add(new BigDecimal("50.00")));
    }

    @Test
    void wholeBalanceCanBeWithdrawn() throws Exception {
        withdraw(newKey(), body(annaAccount, "100.00")).andExpect(status().isCreated());

        assertThat(balance(annaAccount)).isEqualByComparingTo("0.00");
    }

    @Test
    void TC35_withdrawalOutsideAtmLimitsIsRejected() throws Exception {
        for (String amount : new String[] {"15.00", "5010.00", "5.00"}) {
            withdraw(newKey(), body(annaAccount, amount))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("CASH_AMOUNT_INVALID"));
        }
        assertThat(balance(annaAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void TC36_withdrawalFromSavingsIsNotAllowed() throws Exception {
        String opened = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, annaBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\": \"SAVINGS_STANDARD\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID savings = UUID.fromString(JsonPath.read(opened, "$.id"));

        withdraw(newKey(), body(savings, "50.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED_FOR_ACCOUNT"));
    }

    @Test
    void TC37_withdrawalAboveBalanceIsRejected() throws Exception {
        BigDecimal cashBefore = balance(BANK_CASH);

        withdraw(newKey(), body(annaAccount, "110.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        assertThat(balance(annaAccount)).isEqualByComparingTo("100.00");
        assertThat(balance(BANK_CASH)).isEqualByComparingTo(cashBefore);
    }

    @Test
    void blockedAccountCannotSend() throws Exception {
        jdbc.update("UPDATE accounts SET status = 'BLOCKED', status_reason = 'Test' WHERE id = ?", annaAccount);

        withdraw(newKey(), body(annaAccount, "50.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SOURCE_ACCOUNT_UNAVAILABLE"));
        assertThat(balance(annaAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void otherCurrencyIsRejected() throws Exception {
        withdraw(newKey(), "{\"accountId\": \"" + annaAccount + "\", \"amount\": \"50.00\", \"currency\": \"EUR\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_NOT_SUPPORTED"));
    }

    @Test
    void someoneElsesAccountIsNotFound() throws Exception {
        RegisteredCustomer jan = data.registerCustomer();
        UUID janAccount = jdbc.queryForObject("SELECT id FROM accounts WHERE iban = ?", UUID.class, jan.iban());

        withdraw(newKey(), body(janAccount, "10.00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void replayWithSameKeyWithdrawsOnce() throws Exception {
        String key = newKey();
        String first = withdraw(key, body(annaAccount, "50.00"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = withdraw(key, body(annaAccount, "50.00"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(balance(annaAccount)).isEqualByComparingTo("50.00");
    }

    @Test
    void keyOfADepositCannotBeUsedForAWithdrawal() throws Exception {
        String key = newKey();
        call(DEPOSITS, key, body(annaAccount, "50.00")).andExpect(status().isCreated());

        withdraw(key, body(annaAccount, "50.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(balance(annaAccount)).isEqualByComparingTo("150.00");
    }

    @Test
    void missingKeyIsRejected() throws Exception {
        mockMvc.perform(post(WITHDRAWALS)
                        .header(HttpHeaders.AUTHORIZATION, annaBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(annaAccount, "50.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISSING"));
    }

    @Test
    void TC30_parallelWithdrawalsNeverOverdrawTheAccount() throws Exception {
        int requests = 50;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return withdraw(newKey(), body(annaAccount, "10.00")).andReturn().getResponse();
                }));
            }
            start.countDown();
        }

        List<String> outcomes = new ArrayList<>();
        for (Future<MockHttpServletResponse> future : futures) {
            MockHttpServletResponse response = future.get();
            outcomes.add(response.getStatus() == 201
                    ? "201"
                    : response.getStatus() + " " + response.getContentAsString());
        }
        assertThat(outcomes).filteredOn("201"::equals).as(outcomes.toString()).hasSize(10);
        assertThat(outcomes).filteredOn(o -> o.startsWith("422") && o.contains("INSUFFICIENT_FUNDS"))
                .as(outcomes.toString()).hasSize(40);
        assertThat(balance(annaAccount)).isEqualByComparingTo("0.00");
    }

    private ResultActions withdraw(String key, String json) throws Exception {
        return call(WITHDRAWALS, key, json);
    }

    private ResultActions call(String path, String key, String json) throws Exception {
        return mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, annaBearer)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(UUID accountId, String amount) {
        return "{\"accountId\": \"" + accountId + "\", \"amount\": \"" + amount + "\"}";
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

}
