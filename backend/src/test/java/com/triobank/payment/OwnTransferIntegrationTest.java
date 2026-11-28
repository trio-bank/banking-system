package com.triobank.payment;

import static com.triobank.BankApi.SAVINGS;
import static com.triobank.BankApi.assertProblem;
import static com.triobank.BankApi.body;
import static com.triobank.BankApi.idOf;
import static com.triobank.BankApi.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.ApiTestBase;
import com.triobank.BankApi.Customer;
import com.triobank.TestDataFactory;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** US-10 transfer between my accounts, plus the rules every transfer shares. */
class OwnTransferIntegrationTest extends ApiTestBase {

    private static final String FULL_NAME = TestDataFactory.FIRST_NAME + " " + TestDataFactory.LAST_NAME;
    /** IBAN of the bank's cash account (from the migrations). */
    private static final String BANK_CASH_IBAN = "PL25999000129000000000000001";

    // ------------------------------------------------------------------ TC-20

    @Test
    @Tag("TC-20")
    void TC20_personalToSavings_isOwnTransferWithDefaultTitleAndOwnName() throws Exception {
        Customer c = api.newCustomerWithBalance("1000.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");
        String savingsIban = ibanOf(c, savingsId);

        MvcResult result = api.transfer(c.token(), c.personalAccountId(), savingsIban, "Someone Else", null,
                        "150.00", newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OWN_TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.channel").value("WEB"))
                .andExpect(jsonPath("$.amount").value("150.00"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.title").value("Own transfer"))
                .andExpect(jsonPath("$.sender.name").value(FULL_NAME))
                .andExpect(jsonPath("$.sender.iban").value(c.iban()))
                .andExpect(jsonPath("$.sender.ownAccount").value(true))
                .andExpect(jsonPath("$.recipient.name").value(FULL_NAME))
                .andExpect(jsonPath("$.recipient.iban").value(savingsIban))
                .andExpect(jsonPath("$.recipient.ownAccount").value(true))
                .andReturn();

        String transactionId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/transactions/" + transactionId);
        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("850.00");
        assertThat(api.balance(c.token(), savingsId)).isEqualByComparingTo("150.00");

        api.history(c.token(), c.personalAccountId(), 0, 20)
                .andExpect(jsonPath("$.content[0].transactionId").value(transactionId))
                .andExpect(jsonPath("$.content[0].type").value("OWN_TRANSFER"))
                .andExpect(jsonPath("$.content[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value("850.00"));
        api.history(c.token(), savingsId, 0, 20)
                .andExpect(jsonPath("$.content[0].transactionId").value(transactionId))
                .andExpect(jsonPath("$.content[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value("150.00"));
    }

    @Test
    @Tag("TC-20")
    void TC20_givenTitleIsKept_andIbanMayHaveSpacesAndLowerCase() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");
        String typed = withSpaces(ibanOf(c, savingsId)).toLowerCase(Locale.ROOT);

        api.transfer(c.token(), c.personalAccountId(), typed, null, "  For holidays  ", "10", newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("For holidays"))
                .andExpect(jsonPath("$.amount").value("10.00"));
    }

    // ------------------------------------------------------------------ TC-21

    @Test
    @Tag("TC-21")
    void TC21_savingsToPersonal_isAllowed() throws Exception {
        Customer c = api.newCustomerWithBalance("500.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");
        api.transfer(c.token(), c.personalAccountId(), ibanOf(c, savingsId), null, null, "200.00", newKey())
                .andExpect(status().isCreated());

        api.transfer(c.token(), savingsId, c.iban(), null, null, "50.00", newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("OWN_TRANSFER"));

        assertThat(api.balance(c.token(), savingsId)).isEqualByComparingTo("150.00");
        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("350.00");
    }

    // ------------------------------------------------------------------ TC-22

    @Test
    @Tag("TC-22")
    void TC22_sameSourceAndTarget_isRejected() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");

        assertProblem(api.transfer(c.token(), c.personalAccountId(), c.iban(), null, null, "10.00", newKey()),
                422, "SAME_ACCOUNT_TRANSFER");
        assertThat(countTransactionsOfUser(c.userId())).as("only the deposit").isEqualTo(1);
    }

    // ------------------------------------------------------------------ TC-29

    @ParameterizedTest
    @ValueSource(strings = {"10.001", "0", "-5.00", "1000000.01"})
    @Tag("TC-29")
    void TC29_invalidAmount_isFieldError(String amount) throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");

        ResultActions result = api.transfer(c.token(), c.personalAccountId(), ibanOf(c, savingsId), null, null,
                amount, newKey());

        assertProblem(result, 400, "VALIDATION_ERROR");
        result.andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    @Tag("TC-29")
    void TC29_ibanWithWrongCheckDigits_isFieldError() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");

        api.transfer(c.token(), c.personalAccountId(), "PL00999000121000000000000002", null, null, "10.00", newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("targetIban"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_CHECKSUM"));
    }

    @Test
    @Tag("TC-29")
    void TC29_tooLongTitle_isFieldError() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");

        api.transfer(c.token(), c.personalAccountId(), ibanOf(c, savingsId), null, "x".repeat(141), "10.00",
                        newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    // ------------------------------------------------------------------ TC-31

    @Test
    @Tag("TC-31")
    void TC31_sameKeySameRequest_returnsTheOriginalWith200() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");
        String savingsIban = ibanOf(c, savingsId);
        String key = newKey();

        ResultActions first = api.transfer(c.token(), c.personalAccountId(), savingsIban, null, null, "30.00", key)
                .andExpect(status().isCreated());
        // typed differently, but the same transfer
        ResultActions again = api.transfer(c.token(), c.personalAccountId(), withSpaces(savingsIban), null, null,
                "30", key).andExpect(status().isOk());

        assertThat(idOf(again)).isEqualTo(idOf(first));
        assertThat(body(again)).isEqualTo(body(first));
        assertThat(api.balance(c.token(), savingsId)).isEqualByComparingTo("30.00");
        assertThat(countTransactionsOfUser(c.userId())).isEqualTo(2);
    }

    @Test
    @Tag("TC-31")
    void TC31_sameKeyOtherAmount_isRejected() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");
        String key = newKey();
        api.transfer(c.token(), c.personalAccountId(), ibanOf(c, savingsId), null, null, "30.00", key)
                .andExpect(status().isCreated());

        assertProblem(api.transfer(c.token(), c.personalAccountId(), ibanOf(c, savingsId), null, null, "31.00", key),
                409, "IDEMPOTENCY_KEY_REUSED");
        assertThat(api.balance(c.token(), savingsId)).isEqualByComparingTo("30.00");
    }

    @Test
    @Tag("TC-31")
    void TC31_keyOfAnAtmDeposit_cannotBeUsedForATransfer() throws Exception {
        Customer c = api.newCustomer();
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");
        String key = newKey();
        api.deposit(c.token(), c.personalAccountId(), "100.00", key).andExpect(status().isCreated());

        assertProblem(api.transfer(c.token(), c.personalAccountId(), ibanOf(c, savingsId), null, null, "10.00", key),
                409, "IDEMPOTENCY_KEY_REUSED");
    }

    // ------------------------------------------------------- other rejections

    @Test
    void missingIdempotencyKey_isRejected() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");

        assertProblem(api.transfer(c.token(), c.personalAccountId(), ibanOf(c, savingsId), null, null, "10.00", null),
                400, "IDEMPOTENCY_KEY_MISSING");
    }

    @Test
    @Tag("TC-40")
    void sourceAccountOfAnotherCustomer_isNotFound() throws Exception {
        Customer me = api.newCustomerWithBalance("100.00");
        Customer other = api.newCustomerWithBalance("100.00");

        assertProblem(api.transfer(me.token(), other.personalAccountId(), me.iban(), null, null, "10.00", newKey()),
                404, "RESOURCE_NOT_FOUND");
        assertThat(api.balance(other.token(), other.personalAccountId())).isEqualByComparingTo("100.00");
    }

    @Test
    void moreThanTheBalance_isRejectedAndNothingIsStored() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");

        assertProblem(api.transfer(c.token(), c.personalAccountId(), ibanOf(c, savingsId), null, null, "100.01",
                newKey()), 422, "INSUFFICIENT_FUNDS");
        assertThat(countTransactionsOfUser(c.userId())).as("only the deposit").isEqualTo(1);
    }

    @Test
    void bankAccountIban_isNotARecipient() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");

        assertProblem(api.transfer(c.token(), c.personalAccountId(), BANK_CASH_IBAN, "Trio Bank", null, "10.00",
                newKey()), 422, "RECIPIENT_ACCOUNT_NOT_FOUND");
        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("100.00");
    }

    @Test
    void otherCurrency_isNotSupported() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        UUID savingsId = api.openAccountId(c.token(), SAVINGS, "Savings");
        String json = """
                {"sourceAccountId":"%s","targetIban":"%s","amount":10.00,"currency":"EUR"}"""
                .formatted(c.personalAccountId(), ibanOf(c, savingsId));

        assertProblem(mockMvc.perform(post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, TestDataFactory.bearer(c.token()))
                        .header("Idempotency-Key", newKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)),
                422, "CURRENCY_NOT_SUPPORTED");
    }

    // ----------------------------------------------------------------- helpers

    private String ibanOf(Customer c, UUID accountId) throws Exception {
        return JsonPath.read(body(api.account(c.token(), accountId).andExpect(status().isOk())), "$.iban");
    }

    /** "PL45999000…" → "PL45 9990 00…", the way people type it. */
    private static String withSpaces(String iban) {
        return iban.replaceAll("(.{4})(?!$)", "$1 ");
    }

}
