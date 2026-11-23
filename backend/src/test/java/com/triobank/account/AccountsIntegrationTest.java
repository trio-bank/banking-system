package com.triobank.account;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.triobank.AbstractIntegrationTest;
import com.triobank.TestDataFactory;
import com.triobank.TestDataFactory.RegisteredCustomer;
import com.triobank.auth.JwtService;
import com.triobank.auth.Role;
import com.triobank.common.iban.IbanGenerator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * GET /api/v1/accounts and /api/v1/accounts/{id} against a real database. Extra accounts are inserted with SQL,
 * because opening and blocking accounts through the API does not exist yet.
 */
class AccountsIntegrationTest extends AbstractIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final UUID PERSONAL_STANDARD = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID SAVINGS_STANDARD = UUID.fromString("00000000-0000-4000-8000-000000000102");

    @Autowired
    private JwtService jwtService;

    @Autowired
    private IbanGenerator ibanGenerator;

    private RegisteredCustomer anna;
    private String annaToken;

    @BeforeEach
    void registerCustomer() throws Exception {
        anna = data.registerCustomer();
        annaToken = data.loginAndGetToken(anna.email(), anna.password());
    }

    @Test
    void TC18_returnsOnlyOwnAccountsWithPersonalFirst() throws Exception {
        UUID customerId = customerIdOf(anna.userId());
        // opened before the personal account, but must still come after it
        String savings = insertAccount(customerId, SAVINGS_STANDARD, "Holiday savings", "ACTIVE", "-1 day");
        String secondPersonal = insertAccount(customerId, PERSONAL_STANDARD, "Daily expenses", "BLOCKED", "+1 day");
        jdbc.update("UPDATE accounts SET accrued_interest = 0.012345 WHERE iban = ?", savings);

        RegisteredCustomer other = data.registerCustomer();

        mockMvc.perform(get(ACCOUNTS).header(HttpHeaders.AUTHORIZATION, TestDataFactory.bearer(annaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].iban").value(anna.iban()))
                .andExpect(jsonPath("$[0].type").value("PERSONAL"))
                .andExpect(jsonPath("$[0].balance").value("0.00"))
                .andExpect(jsonPath("$[0].accruedInterest").value(nullValue()))
                .andExpect(jsonPath("$[1].iban").value(secondPersonal))
                .andExpect(jsonPath("$[1].status").value("BLOCKED"))
                .andExpect(jsonPath("$[2].iban").value(savings))
                .andExpect(jsonPath("$[2].type").value("SAVINGS"))
                .andExpect(jsonPath("$[2].productCode").value("SAVINGS_STANDARD"))
                .andExpect(jsonPath("$[2].interestRate").value("4.0000"))
                .andExpect(jsonPath("$[2].accruedInterest").value("0.012345"))
                .andExpect(jsonPath("$[*].iban", not(hasItem(other.iban()))));
    }

    @Test
    void TC18_returnsOwnSavingsAccountWithRateAndAccruedInterest() throws Exception {
        String savings = insertAccount(customerIdOf(anna.userId()), SAVINGS_STANDARD, "Holiday savings",
                "ACTIVE", "+1 day");
        jdbc.update("UPDATE accounts SET accrued_interest = 0.012345 WHERE iban = ?", savings);

        mockMvc.perform(get(ACCOUNTS + "/" + accountIdOf(savings))
                        .header(HttpHeaders.AUTHORIZATION, TestDataFactory.bearer(annaToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(savings))
                .andExpect(jsonPath("$.name").value("Holiday savings"))
                .andExpect(jsonPath("$.type").value("SAVINGS"))
                .andExpect(jsonPath("$.balance").value("0.00"))
                .andExpect(jsonPath("$.interestRate").value("4.0000"))
                .andExpect(jsonPath("$.accruedInterest").value("0.012345"));
    }

    @Test
    void TC18_anotherCustomersAccountLooksMissing() throws Exception {
        RegisteredCustomer other = data.registerCustomer();
        String bearer = TestDataFactory.bearer(annaToken);

        for (UUID id : new UUID[] {accountIdOf(other.iban()), UUID.randomUUID()}) {
            mockMvc.perform(get(ACCOUNTS + "/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("Account not found."));
        }
    }

    @Test
    void malformedAccountIdIsBadRequest() throws Exception {
        mockMvc.perform(get(ACCOUNTS + "/not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, TestDataFactory.bearer(annaToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void adminGets403OnCustomerEndpoints() throws Exception {
        String admin = TestDataFactory.bearer(jwtService.issue(UUID.randomUUID(), Role.ADMIN, null).value());

        mockMvc.perform(get(ACCOUNTS).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        for (String path : new String[] {"/api/v1/account-products", "/api/v1/transactions/" + UUID.randomUUID()}) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        for (String path : new String[] {"/api/v1/atm/deposits", "/api/v1/transfers"}) {
            mockMvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void withoutTokenGets401() throws Exception {
        mockMvc.perform(get(ACCOUNTS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private UUID customerIdOf(UUID userId) {
        return jdbc.queryForObject("SELECT id FROM customers WHERE user_id = ?", UUID.class, userId);
    }

    private UUID accountIdOf(String iban) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE iban = ?", UUID.class, iban);
    }

    /** @param openedShift shift from the opening time of the customer's first account, as a Postgres interval */
    private String insertAccount(UUID customerId, UUID productId, String name, String status, String openedShift) {
        String number = jdbc.queryForObject("SELECT CAST(nextval('account_number_seq') AS VARCHAR)", String.class);
        String iban = ibanGenerator.generate(number);
        jdbc.update("""
                INSERT INTO accounts (id, iban, category, customer_id, product_id, name, currency, status,
                                      opened_at, created_at, updated_at)
                SELECT ?, ?, 'CUSTOMER', ?, ?, ?, 'PLN', ?,
                       a.opened_at + CAST(? AS INTERVAL), now(), now()
                FROM accounts a WHERE a.iban = ?""",
                UUID.randomUUID(), iban, customerId, productId, name, status, openedShift, anna.iban());
        return iban;
    }

}
