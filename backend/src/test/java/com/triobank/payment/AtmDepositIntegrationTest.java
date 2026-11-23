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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * POST /api/v1/atm/deposits against a real database.
 */
class AtmDepositIntegrationTest extends AbstractIntegrationTest {

    private static final String DEPOSITS = "/api/v1/atm/deposits";
    private static final UUID BANK_CASH = UUID.fromString("00000000-0000-4000-8000-000000000201");

    private String annaBearer;
    private UUID annaAccount;

    @BeforeEach
    void registerCustomer() throws Exception {
        RegisteredCustomer anna = data.registerCustomer();
        annaBearer = TestDataFactory.bearer(data.loginAndGetToken(anna.email(), anna.password()));
        annaAccount = accountId(anna.iban());
    }

    @AfterEach
    void ledgerStaysBalanced() {
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    @Test
    void TC34_depositMovesCashFromBankToAccount() throws Exception {
        BigDecimal cashBefore = balance(BANK_CASH);

        MvcResult result = deposit(newKey(), body(annaAccount, "\"200.00\"", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CASH_DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.channel").value("ATM"))
                .andExpect(jsonPath("$.amount").value("200.00"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.title").value("ATM cash deposit"))
                .andExpect(jsonPath("$.sender.name").value("Trio Bank"))
                .andExpect(jsonPath("$.sender.iban").value((Object) null))
                .andExpect(jsonPath("$.sender.ownAccount").value(false))
                .andExpect(jsonPath("$.recipient.name").value("Anna Nowak"))
                .andExpect(jsonPath("$.recipient.ownAccount").value(true))
                .andExpect(jsonPath("$.reference").isString())
                .andExpect(jsonPath("$.bookedAt").isString())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/transactions/" + id);
        assertThat(balance(annaAccount)).isEqualByComparingTo("200.00");
        assertThat(balance(BANK_CASH)).isEqualByComparingTo(cashBefore.subtract(new BigDecimal("200.00")));
    }

    @Test
    void TC35_amountOutsideAtmLimitsIsRejected() throws Exception {
        for (String amount : new String[] {"\"15.00\"", "\"25000.00\"", "\"5.00\""}) {
            deposit(newKey(), body(annaAccount, amount, null))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("CASH_AMOUNT_INVALID"));
        }
        assertThat(balance(annaAccount)).isEqualByComparingTo("0.00");
    }

    @Test
    void otherCurrencyIsRejected() throws Exception {
        deposit(newKey(), body(annaAccount, "\"200.00\"", "\"EUR\""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_NOT_SUPPORTED"));
    }

    @Test
    void explicitPlnIsAccepted() throws Exception {
        deposit(newKey(), body(annaAccount, "\"200.00\"", "\"PLN\""))
                .andExpect(status().isCreated());
    }

    @Test
    void amountWithThreeDecimalsIsValidationError() throws Exception {
        MvcResult result = deposit(newKey(), body(annaAccount, "\"10.001\"", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(TestDataFactory.fieldCodes(result, "amount")).containsExactly("TOO_MANY_DECIMALS");
    }

    @Test
    void amountAboveOperationLimitIsValidationError() throws Exception {
        MvcResult result = deposit(newKey(), body(annaAccount, "\"1000000.01\"", null))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(TestDataFactory.fieldCodes(result, "amount")).containsExactly("OUT_OF_RANGE");
    }

    @Test
    void missingFieldsAreRequired() throws Exception {
        MvcResult result = deposit(newKey(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(TestDataFactory.fieldCodes(result, "accountId")).containsExactly("REQUIRED");
        assertThat(TestDataFactory.fieldCodes(result, "amount")).containsExactly("REQUIRED");
    }

    @Test
    void replayWithSameKeyReturnsTheOriginalTransaction() throws Exception {
        String key = newKey();
        String first = deposit(key, body(annaAccount, "\"200.00\"", null))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // same request, amount written differently and currency spelled out
        String second = deposit(key, body(annaAccount, "\"200\"", "\"PLN\""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(balance(annaAccount)).isEqualByComparingTo("200.00");
        assertThat(countRows("SELECT count(*) FROM transactions WHERE target_account_id = ?", annaAccount))
                .isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentAmountIsRejected() throws Exception {
        String key = newKey();
        deposit(key, body(annaAccount, "\"200.00\"", null)).andExpect(status().isCreated());

        deposit(key, body(annaAccount, "\"300.00\"", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(balance(annaAccount)).isEqualByComparingTo("200.00");
    }

    @Test
    void missingKeyIsRejected() throws Exception {
        mockMvc.perform(post(DEPOSITS)
                        .header(HttpHeaders.AUTHORIZATION, annaBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(annaAccount, "\"200.00\"", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISSING"));
    }

    @Test
    void someoneElsesAccountIsNotFound() throws Exception {
        RegisteredCustomer jan = data.registerCustomer();

        deposit(newKey(), body(accountId(jan.iban()), "\"200.00\"", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(balance(accountId(jan.iban()))).isEqualByComparingTo("0.00");
    }

    @Test
    void savingsAccountIsNotAllowed() throws Exception {
        String opened = mockMvc.perform(post("/api/v1/accounts")
                        .header(HttpHeaders.AUTHORIZATION, annaBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\": \"SAVINGS_STANDARD\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID savings = UUID.fromString(JsonPath.read(opened, "$.id"));

        deposit(newKey(), body(savings, "\"200.00\"", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED_FOR_ACCOUNT"));
    }

    @Test
    void blockedAccountCannotReceive() throws Exception {
        jdbc.update("UPDATE accounts SET status = 'BLOCKED', status_reason = 'Test' WHERE id = ?", annaAccount);

        deposit(newKey(), body(annaAccount, "\"200.00\"", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECIPIENT_ACCOUNT_UNAVAILABLE"));
        assertThat(balance(annaAccount)).isEqualByComparingTo("0.00");
    }

    private ResultActions deposit(String key, String json) throws Exception {
        return mockMvc.perform(post(DEPOSITS)
                .header(HttpHeaders.AUTHORIZATION, annaBearer)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(UUID accountId, String amount, String currency) {
        return "{\"accountId\": \"" + accountId + "\", \"amount\": " + amount + ", \"currency\": " + currency + "}";
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private UUID accountId(String iban) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE iban = ?", UUID.class, iban);
    }

    private BigDecimal balance(UUID accountId) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

}
