package com.triobank.payment;

import static com.triobank.BankApi.SAVINGS;
import static com.triobank.BankApi.assertProblem;
import static com.triobank.BankApi.body;
import static com.triobank.BankApi.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.ApiTestBase;
import com.triobank.BankApi.Customer;
import com.triobank.TestDataFactory;
import com.triobank.common.iban.IbanGenerator;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** US-11 transfer to another customer. */
class TransferToCustomerIntegrationTest extends ApiTestBase {

    private static final String SENDER_NAME = TestDataFactory.FIRST_NAME + " " + TestDataFactory.LAST_NAME;
    private static final String RECIPIENT_NAME = "Jan Kowalski";
    /** Valid IBAN of another bank (sort code 10901014). */
    private static final String OTHER_BANK_IBAN = "PL61109010140000071219812874";

    @Autowired
    private IbanGenerator ibanGenerator;

    // ------------------------------------------------------------------ TC-23

    @Test
    @Tag("TC-23")
    void TC23_internalTransfer_movesMoneyBetweenCustomers() throws Exception {
        Customer a = api.newCustomerWithBalance("1000.00");
        Customer b = api.newCustomer();

        MvcResult result = api.transfer(a.token(), a.personalAccountId(), b.iban(), RECIPIENT_NAME, null,
                        "150.00", newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.channel").value("WEB"))
                .andExpect(jsonPath("$.amount").value("150.00"))
                .andExpect(jsonPath("$.title").value("Funds transfer"))
                .andExpect(jsonPath("$.sender.name").value(SENDER_NAME))
                .andExpect(jsonPath("$.sender.iban").value(a.iban()))
                .andExpect(jsonPath("$.sender.ownAccount").value(true))
                .andExpect(jsonPath("$.recipient.name").value(RECIPIENT_NAME))
                .andExpect(jsonPath("$.recipient.iban").value(b.iban()))
                .andExpect(jsonPath("$.recipient.ownAccount").value(false))
                .andReturn();
        String transactionId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        assertThat(api.balance(a.token(), a.personalAccountId())).isEqualByComparingTo("850.00");
        assertThat(api.balance(b.token(), b.personalAccountId())).isEqualByComparingTo("150.00");

        api.history(a.token(), a.personalAccountId(), 0, 20)
                .andExpect(jsonPath("$.content[0].transactionId").value(transactionId))
                .andExpect(jsonPath("$.content[0].type").value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.content[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value("850.00"))
                .andExpect(jsonPath("$.content[0].counterpartyName").value(RECIPIENT_NAME))
                .andExpect(jsonPath("$.content[0].counterpartyIban").value(b.iban()));
        api.history(b.token(), b.personalAccountId(), 0, 20)
                .andExpect(jsonPath("$.content[0].transactionId").value(transactionId))
                .andExpect(jsonPath("$.content[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value("150.00"))
                .andExpect(jsonPath("$.content[0].counterpartyName").value(SENDER_NAME))
                .andExpect(jsonPath("$.content[0].counterpartyIban").value(a.iban()));
    }

    @Test
    @Tag("TC-23")
    void TC23_givenTitleIsKept() throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        Customer b = api.newCustomer();

        api.transfer(a.token(), a.personalAccountId(), b.iban(), RECIPIENT_NAME, "  Dinner  ", "20.00", newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Dinner"));
    }

    // ------------------------------------------------------------------ TC-24

    @Test
    @Tag("TC-24")
    void TC24_moreThanTheBalance_isRejectedAndNothingIsStored() throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        Customer b = api.newCustomer();

        assertProblem(transfer(a, b.iban(), "100.01"), 422, "INSUFFICIENT_FUNDS");

        assertThat(countTransactionsOfUser(a.userId())).as("only the deposit").isEqualTo(1);
        assertThat(api.balance(a.token(), a.personalAccountId())).isEqualByComparingTo("100.00");
        assertThat(api.balance(b.token(), b.personalAccountId())).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------ TC-25

    @Test
    @Tag("TC-25")
    void TC25_fromSavingsToAnotherCustomer_isRejected() throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        Customer b = api.newCustomer();
        UUID savingsId = api.openAccountId(a.token(), SAVINGS, "Savings");
        api.transfer(a.token(), a.personalAccountId(), ibanOf(a, savingsId), null, null, "50.00", newKey())
                .andExpect(status().isCreated());

        assertProblem(api.transfer(a.token(), savingsId, b.iban(), RECIPIENT_NAME, null, "10.00", newKey()),
                422, "TRANSFER_POLICY_VIOLATION");

        assertThat(api.balance(a.token(), savingsId)).isEqualByComparingTo("50.00");
        assertThat(api.balance(b.token(), b.personalAccountId())).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------ TC-26

    @Test
    @Tag("TC-26")
    void TC26_ibanOfAnotherBank_isNotSupported() throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");

        assertProblem(transfer(a, OTHER_BANK_IBAN, "10.00"), 422, "EXTERNAL_TRANSFER_NOT_SUPPORTED");
        assertThat(api.balance(a.token(), a.personalAccountId())).isEqualByComparingTo("100.00");
    }

    // ------------------------------------------------------------------ TC-27

    @Test
    @Tag("TC-27")
    void TC27_trioIbanThatDoesNotExist_isNotFound() throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        // valid check digits and our sort code, but no account has this number
        String unknown = ibanGenerator.generate("9999999999999999");

        assertProblem(transfer(a, unknown, "10.00"), 422, "RECIPIENT_ACCOUNT_NOT_FOUND");
    }

    // ------------------------------------------------------------------ TC-28

    @ParameterizedTest
    @ValueSource(strings = {"BLOCKED", "CLOSED"})
    @Tag("TC-28")
    void TC28_recipientAccountNotActive_isUnavailable(String status) throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        Customer b = api.newCustomer();
        setAccountStatus(b.personalAccountId(), status);

        assertProblem(transfer(a, b.iban(), "10.00"), 422, "RECIPIENT_ACCOUNT_UNAVAILABLE");
        assertThat(api.balance(a.token(), a.personalAccountId())).isEqualByComparingTo("100.00");
    }

    @Test
    @Tag("TC-28")
    void TC28_blockedSourceAccount_isUnavailable() throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        Customer b = api.newCustomer();
        setAccountStatus(a.personalAccountId(), "BLOCKED");

        assertProblem(transfer(a, b.iban(), "10.00"), 422, "SOURCE_ACCOUNT_UNAVAILABLE");
        assertThat(api.balance(b.token(), b.personalAccountId())).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------------ TC-29

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @Tag("TC-29")
    void TC29_missingRecipientName_isFieldError(String name) throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        Customer b = api.newCustomer();

        api.transfer(a.token(), a.personalAccountId(), b.iban(), name, null, "10.00", newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("recipientName"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    @Tag("TC-29")
    void TC29_recipientNameLeftOut_isFieldError() throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        Customer b = api.newCustomer();

        transfer(a, b.iban(), null, "10.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipientName"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        assertThat(api.balance(b.token(), b.personalAccountId())).isEqualByComparingTo("0.00");
    }

    @Test
    @Tag("TC-29")
    void TC29_tooLongRecipientName_isFieldError() throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        Customer b = api.newCustomer();

        transfer(a, b.iban(), "x".repeat(141), "10.00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipientName"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    }

    @Test
    @Tag("TC-29")
    void TC29_recipientNameIsCheckedBeforeTheSavingsPolicy() throws Exception {
        Customer a = api.newCustomerWithBalance("100.00");
        Customer b = api.newCustomer();
        UUID savingsId = api.openAccountId(a.token(), SAVINGS, "Savings");
        api.transfer(a.token(), a.personalAccountId(), ibanOf(a, savingsId), null, null, "50.00", newKey())
                .andExpect(status().isCreated());

        api.transfer(a.token(), savingsId, b.iban(), null, null, "10.00", newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipientName"));
    }

    // ----------------------------------------------------------------- helpers

    private ResultActions transfer(Customer from, String targetIban, String amount) throws Exception {
        return transfer(from, targetIban, RECIPIENT_NAME, amount);
    }

    private ResultActions transfer(Customer from, String targetIban, String recipientName, String amount)
            throws Exception {
        return api.transfer(from.token(), from.personalAccountId(), targetIban, recipientName, null, amount,
                newKey());
    }

    private String ibanOf(Customer c, UUID accountId) throws Exception {
        return JsonPath.read(body(api.account(c.token(), accountId).andExpect(status().isOk())), "$.iban");
    }

}
