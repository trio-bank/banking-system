package com.triobank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Accounts and transactions part of the test data (extends {@link TestDataFactory}, which registers and logs in).
 * Everything goes through the public API, so the data always satisfies the ledger invariants.
 */
public final class BankApi {

    public static final String PERSONAL = "PERSONAL_STANDARD";
    public static final String SAVINGS = "SAVINGS_STANDARD";

    private static final String ACCOUNTS = "/api/v1/accounts";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** A registered and logged in customer. */
    public record Customer(UUID userId, String email, String password, String pesel, String iban,
                           String token, UUID personalAccountId) {
    }

    private final MockMvc mvc;
    private final TestDataFactory factory;

    public BankApi(MockMvc mvc, TestDataFactory factory) {
        this.mvc = mvc;
        this.factory = factory;
    }

    // ------------------------------------------------------------ customers

    public Customer newCustomer() throws Exception {
        TestDataFactory.RegisteredCustomer reg = factory.registerCustomer();
        String token = factory.loginAndGetToken(reg.email(), reg.password());
        String accounts = body(accounts(token).andExpect(status().isOk()));
        UUID personalId = UUID.fromString(JsonPath.read(accounts, "$[0].id")); // personal account comes first
        return new Customer(reg.userId(), reg.email(), reg.password(), reg.pesel(), reg.iban(), token, personalId);
    }

    /** New customer with money on the personal account (deposit through the ATM, max 20 000.00). */
    public Customer newCustomerWithBalance(String amount) throws Exception {
        Customer customer = newCustomer();
        deposit(customer.token(), customer.personalAccountId(), amount, newKey()).andExpect(status().isCreated());
        return customer;
    }

    // ------------------------------------------------------------- requests

    public ResultActions me(String token) throws Exception {
        return mvc.perform(auth(get("/api/v1/me"), token));
    }

    public ResultActions accounts(String token) throws Exception {
        return mvc.perform(auth(get(ACCOUNTS), token));
    }

    public ResultActions account(String token, UUID accountId) throws Exception {
        return mvc.perform(auth(get(ACCOUNTS + "/" + accountId), token));
    }

    /** @param name null to get the default name of the product */
    public ResultActions open(String token, String productCode, String name) throws Exception {
        String json = name == null
                ? "{\"productCode\":\"%s\"}".formatted(productCode)
                : "{\"productCode\":\"%s\",\"name\":\"%s\"}".formatted(productCode, name);
        return mvc.perform(auth(post(ACCOUNTS), token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public UUID openAccountId(String token, String productCode, String name) throws Exception {
        return idOf(open(token, productCode, name).andExpect(status().isCreated()));
    }

    public ResultActions rename(String token, UUID accountId, String name) throws Exception {
        return mvc.perform(auth(patch(ACCOUNTS + "/" + accountId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name)));
    }

    public ResultActions history(String token, UUID accountId, int page, int size) throws Exception {
        return mvc.perform(auth(get(ACCOUNTS + "/" + accountId + "/history"), token)
                .param("page", String.valueOf(page)).param("size", String.valueOf(size)));
    }

    public ResultActions deposit(String token, UUID accountId, String amount, String key) throws Exception {
        return cash("deposits", token, accountId, amount, null, key);
    }

    public ResultActions withdraw(String token, UUID accountId, String amount, String key) throws Exception {
        return cash("withdrawals", token, accountId, amount, null, key);
    }

    /**
     * @param operation "deposits" or "withdrawals"
     * @param amount    raw JSON number, e.g. {@code "200.00"}, so odd values like {@code "10.001"} can be sent
     * @param currency  null to leave it out
     * @param key       null to send no Idempotency-Key header
     */
    public ResultActions cash(String operation, String token, UUID accountId, String amount, String currency,
                              String key) throws Exception {
        String extra = currency == null ? "" : ",\"currency\":\"" + currency + "\"";
        MockHttpServletRequestBuilder request = auth(post("/api/v1/atm/" + operation), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"%s\",\"amount\":%s%s}".formatted(accountId, amount, extra));
        if (key != null) {
            request.header(IDEMPOTENCY_KEY, key);
        }
        return mvc.perform(request);
    }

    /**
     * @param recipientName null to leave it out
     * @param title         null to leave it out
     * @param amount        raw JSON number, e.g. {@code "150.00"}
     * @param key           null to send no Idempotency-Key header
     */
    public ResultActions transfer(String token, UUID sourceAccountId, String targetIban, String recipientName,
                                  String title, String amount, String key) throws Exception {
        MockHttpServletRequestBuilder request = auth(post("/api/v1/transfers"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAccountId\":\"%s\",\"targetIban\":%s,\"recipientName\":%s,\"title\":%s,\"amount\":%s}"
                        .formatted(sourceAccountId, jsonString(targetIban), jsonString(recipientName),
                                jsonString(title), amount));
        if (key != null) {
            request.header(IDEMPOTENCY_KEY, key);
        }
        return mvc.perform(request);
    }

    // -------------------------------------------------------------- helpers

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    public BigDecimal balance(String token, UUID accountId) throws Exception {
        return dec(JsonPath.read(body(account(token, accountId).andExpect(status().isOk())), "$.balance"));
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    public static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    public static UUID idOf(ResultActions result) throws Exception {
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
    }

    /** Money comes as a string ("10.00"), rates may come as numbers; both become a BigDecimal. */
    public static BigDecimal dec(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    /** Checks the HTTP status and the {@code code} of a Problem Details error. */
    public static void assertProblem(ResultActions result, int httpStatus, String code) throws Exception {
        result.andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestDataFactory.bearer(token));
    }

}
