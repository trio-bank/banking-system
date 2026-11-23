package com.triobank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.AbstractIntegrationTest;
import com.triobank.TestDataFactory;
import com.triobank.TestDataFactory.RegisteredCustomer;
import com.triobank.auth.JwtService;
import com.triobank.auth.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GET /api/v1/account-products and POST /api/v1/accounts against a real database.
 */
class OpenAccountIntegrationTest extends AbstractIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String PRODUCTS = "/api/v1/account-products";

    @Autowired
    private JwtService jwtService;

    private RegisteredCustomer anna;
    private String annaBearer;

    @BeforeEach
    void registerCustomer() throws Exception {
        anna = data.registerCustomer();
        annaBearer = TestDataFactory.bearer(data.loginAndGetToken(anna.email(), anna.password()));
    }

    @Test
    void productCatalogListsActiveProducts() throws Exception {
        mockMvc.perform(get(PRODUCTS).header(HttpHeaders.AUTHORIZATION, annaBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("PERSONAL_STANDARD"))
                .andExpect(jsonPath("$[0].type").value("PERSONAL"))
                .andExpect(jsonPath("$[0].atmAllowed").value(true))
                .andExpect(jsonPath("$[0].maxAccountsPerCustomer").value(2))
                .andExpect(jsonPath("$[0].prerequisiteType").doesNotExist())
                .andExpect(jsonPath("$[1].code").value("SAVINGS_STANDARD"))
                .andExpect(jsonPath("$[1].name").value("Trio Savings Account"))
                .andExpect(jsonPath("$[1].currency").value("PLN"))
                .andExpect(jsonPath("$[1].interestRate").value("4.0000"))
                .andExpect(jsonPath("$[1].outgoingTransferPolicy").value("OWN_ACCOUNTS_ONLY"))
                .andExpect(jsonPath("$[1].atmAllowed").value(false))
                .andExpect(jsonPath("$[1].maxAccountsPerCustomer").value(2))
                .andExpect(jsonPath("$[1].prerequisiteType").value("PERSONAL"));
    }

    @Test
    void TC15_opensSavingsWithDefaultNameAndLocation() throws Exception {
        MvcResult result = open(annaBearer, "SAVINGS_STANDARD", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Trio Savings Account"))
                .andExpect(jsonPath("$.type").value("SAVINGS"))
                .andExpect(jsonPath("$.productCode").value("SAVINGS_STANDARD"))
                .andExpect(jsonPath("$.currency").value("PLN"))
                .andExpect(jsonPath("$.balance").value("0.00"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.interestRate").value("4.0000"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String iban = JsonPath.read(body, "$.iban");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(ACCOUNTS + "/" + id);
        assertThat(TestDataFactory.isValidPolishIban(iban)).isTrue();
        assertThat(iban).isNotEqualTo(anna.iban());
        assertThat(countRows("SELECT count(*) FROM accounts WHERE iban = ?", iban)).isEqualTo(1);
        assertThat(countRows("SELECT count(*) FROM audit_events WHERE action = 'ACCOUNT_OPENED' AND target_id = ?",
                UUID.fromString(id))).isEqualTo(1);

        mockMvc.perform(get(ACCOUNTS + "/" + id).header(HttpHeaders.AUTHORIZATION, annaBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(iban));
    }

    @Test
    void givenNameIsTrimmed() throws Exception {
        open(annaBearer, "PERSONAL_STANDARD", "\"  Daily expenses  \"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Daily expenses"))
                .andExpect(header().exists(HttpHeaders.LOCATION));
    }

    @Test
    void blankNameBecomesProductName() throws Exception {
        open(annaBearer, "PERSONAL_STANDARD", "\"   \"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Trio Personal Account"));
    }

    @Test
    void nameOf51CharactersIsRejected() throws Exception {
        MvcResult result = open(annaBearer, "PERSONAL_STANDARD", "\"" + "a".repeat(51) + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(TestDataFactory.fieldCodes(result, "name")).containsExactly("TOO_LONG");
    }

    @Test
    void nameOf50CharactersAfterTrimmingIsAccepted() throws Exception {
        open(annaBearer, "PERSONAL_STANDARD", "\" " + "a".repeat(50) + " \"")
                .andExpect(status().isCreated());
    }

    @Test
    void missingProductCodeIsRejected() throws Exception {
        MvcResult result = open(annaBearer, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(TestDataFactory.fieldCodes(result, "productCode")).containsExactly("REQUIRED");
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        open(annaBearer, "GOLD_STANDARD", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Product not found."));
    }

    @Test
    void TC16_thirdPersonalAccountIsRejected() throws Exception {
        open(annaBearer, "PERSONAL_STANDARD", null).andExpect(status().isCreated());

        open(annaBearer, "PERSONAL_STANDARD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LIMIT_REACHED"));
        assertThat(accountsOfAnna()).isEqualTo(2);
    }

    @Test
    void TC16_thirdSavingsAccountIsRejected() throws Exception {
        open(annaBearer, "SAVINGS_STANDARD", null).andExpect(status().isCreated());
        open(annaBearer, "SAVINGS_STANDARD", null).andExpect(status().isCreated());

        open(annaBearer, "SAVINGS_STANDARD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LIMIT_REACHED"));
        assertThat(accountsOfAnna()).isEqualTo(3);
    }

    @Test
    void blockedAccountsCountTowardsTheLimitButClosedOnesDoNot() throws Exception {
        String second = JsonPath.read(open(annaBearer, "PERSONAL_STANDARD", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        jdbc.update("UPDATE accounts SET status = 'BLOCKED' WHERE id = ?", UUID.fromString(second));

        open(annaBearer, "PERSONAL_STANDARD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LIMIT_REACHED"));

        jdbc.update("UPDATE accounts SET status = 'CLOSED' WHERE id = ?", UUID.fromString(second));

        open(annaBearer, "PERSONAL_STANDARD", null).andExpect(status().isCreated());
    }

    @Test
    void TC17_savingsWhenPersonalIsBlockedIsRejected() throws Exception {
        jdbc.update("UPDATE accounts SET status = 'BLOCKED' WHERE iban = ?", anna.iban());

        open(annaBearer, "SAVINGS_STANDARD", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_ACCOUNT_REQUIRED"));
        assertThat(accountsOfAnna()).isEqualTo(1);
    }

    @Test
    void parallelRequestsDoNotExceedTheLimit() throws Exception {
        int requests = 4;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            tasks.add(() -> {
                start.await();
                return open(annaBearer, "PERSONAL_STANDARD", null).andReturn().getResponse().getStatus();
            });
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            List<Future<Integer>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        }

        // Anna already has one personal account, so only one more fits.
        assertThat(statuses).containsOnly(201, 422);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(accountsOfAnna()).isEqualTo(2);
    }

    @Test
    void adminCannotOpenAccounts() throws Exception {
        String admin = TestDataFactory.bearer(jwtService.issue(UUID.randomUUID(), Role.ADMIN, null).value());

        open(admin, "PERSONAL_STANDARD", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void withoutTokenGets401() throws Exception {
        mockMvc.perform(post(ACCOUNTS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\": \"PERSONAL_STANDARD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(PRODUCTS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /**
     * @param productCode raw value, quoted here; null leaves the field out
     * @param nameJson    raw JSON value of name, e.g. {@code "\"Savings\""}; null leaves the field out
     */
    private ResultActions open(String bearer, String productCode, String nameJson) throws Exception {
        List<String> fields = new ArrayList<>();
        if (productCode != null) {
            fields.add("\"productCode\": \"" + productCode + "\"");
        }
        if (nameJson != null) {
            fields.add("\"name\": " + nameJson);
        }
        return mockMvc.perform(post(ACCOUNTS)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + String.join(", ", fields) + "}"));
    }

    private long accountsOfAnna() {
        return countRows("""
                SELECT count(*) FROM accounts a JOIN customers c ON c.id = a.customer_id
                WHERE c.user_id = ?""", anna.userId());
    }

}
