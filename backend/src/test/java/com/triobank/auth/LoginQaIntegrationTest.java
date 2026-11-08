package com.triobank.auth;

import static com.triobank.TestDataFactory.PASSWORD;
import static com.triobank.TestDataFactory.WRONG_PASSWORD;
import static com.triobank.TestDataFactory.bearer;
import static com.triobank.TestDataFactory.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.AbstractIntegrationTest;
import com.triobank.TestDataFactory.RegisteredCustomer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;


/** US-02 login, lockout and /me: TC-07...TC-10 and TC-13 from docs/09-testing.md §5.1. */
class LoginQaIntegrationTest extends AbstractIntegrationTest {

    // security.lockout.* in application.yml
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void TC07_login_correctCredentials_returnsTokenWithClaimsAndUpdatesLastLogin() throws Exception {
        RegisteredCustomer customer = data.registerCustomer();
        Instant beforeFirstLogin = Instant.now();

        String body = data.login(customer.email(), customer.password())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.id").value(customer.userId().toString()))
                .andExpect(jsonPath("$.user.email").value(customer.email()))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.lastLoginAt").doesNotExist())   // first login: no previous one
                .andExpect(jsonPath("$.user.customer").doesNotExist())      // full profile comes from /me
                .andReturn().getResponse().getContentAsString();

        // claims inside the token
        String token = JsonPath.read(body, "$.accessToken");
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        UUID customerId = jdbc.queryForObject("select id from customers where user_id = ?",
                UUID.class, customer.userId());
        assertThat(JsonPath.<String>read(claims, "$.sub")).isEqualTo(customer.userId().toString());
        assertThat(JsonPath.<String>read(claims, "$.role")).isEqualTo("CUSTOMER");
        assertThat(JsonPath.<String>read(claims, "$.cid")).isEqualTo(String.valueOf(customerId));
        long issuedAt = JsonPath.<Number>read(claims, "$.iat").longValue();
        long expiresAt = JsonPath.<Number>read(claims, "$.exp").longValue();
        assertThat(expiresAt - issuedAt).isEqualTo(1800);

        // last login is stored and shown on the next login
        assertThat(jdbc.queryForObject("select last_login_at is not null from users where id = ?",
                Boolean.class, customer.userId())).isTrue();
        String second = data.login(customer.email(), customer.password())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.lastLoginAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Instant previousLogin = Instant.parse(JsonPath.read(second, "$.user.lastLoginAt"));
        assertThat(previousLogin).isBetween(beforeFirstLogin.minusSeconds(2), Instant.now().plusSeconds(2));
    }

    @Test
    void TC08_login_wrongPasswordAndUnknownEmail_returnIdenticalErrors() throws Exception {
        RegisteredCustomer customer = data.registerCustomer();

        MvcResult wrongPassword = data.login(customer.email(), WRONG_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();
        MvcResult unknownEmail = data.login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        // nobody can tell from the response which emails are registered
        assertThat(withoutVolatileFields(unknownEmail)).isEqualTo(withoutVolatileFields(wrongPassword));
    }

    @Test
    void TC09_login_fiveWrongPasswords_locksUserAndUnlocksAfterFifteenMinutes() throws Exception {
        RegisteredCustomer customer = data.registerCustomer();

        for (int attempt = 1; attempt <= MAX_FAILED_ATTEMPTS; attempt++) {
            data.login(customer.email(), WRONG_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        // the 6th attempt is refused even with the right password
        MvcResult locked = data.login(customer.email(), customer.password())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_LOCKED"))
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty())
                .andReturn();
        Instant lockedUntil = Instant.parse(JsonPath.read(locked.getResponse().getContentAsString(), "$.lockedUntil"));
        Instant expected = Instant.now().plus(LOCK_DURATION);
        assertThat(lockedUntil).isBetween(expected.minusSeconds(60), expected.plusSeconds(5));
        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, customer.userId()))
                .isEqualTo("LOCKED");
        assertThat(countRows("select count(*) from audit_events where action = 'USER_LOCKED' and target_id = ?",
                 customer.userId())).isEqualTo(1);

        // after the lock time the right password works again
        clock.advance(LOCK_DURATION.plusSeconds(5));
        data.login(customer.email(), customer.password())
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from users where id = ?", String.class, customer.userId()))
                .isEqualTo("ACTIVE");
        assertThat(countRows("select failed_login_attempts from users where id = ?", customer.userId())).isZero();
    }

    @Test
    void TC10_login_blockedUser_returns403() throws Exception {
        RegisteredCustomer customer = data.registerCustomer();
        jdbc.update("update users set status = 'BLOCKED', status_reason = ? where id = ?",
                "Blocked by a test", customer.userId());

        data.login(customer.email(), customer.password())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    }

    @Test
    void TC13_me_forCustomer_returnsProfileWithMaskedPesel() throws Exception {
        RegisteredCustomer customer = data.registerCustomer();
        String token = data.loginAndGetToken(customer.email(), customer.password());

        String body = mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.userId().toString()))
                .andExpect(jsonPath("$.email").value(customer.email()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.customer.customerNumber").value(customer.customerNumber()))
                .andExpect(jsonPath("$.customer.firstName").value("Anna"))
                .andExpect(jsonPath("$.customer.lastName").value("Nowak"))
                .andExpect(jsonPath("$.customer.dateOfBirth").value("1990-05-14"))
                .andExpect(jsonPath("$.customer.peselMasked").value("*******" + customer.pesel().substring(7)))
                .andExpect(jsonPath("$.customer.pesel").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(customer.pesel());
    }

    @Test
    void TC13_me_forAdmin_hasNoCustomerProfile() throws Exception {
        String email = uniqueEmail();
        users.save(new User(email, passwordEncoder.encode(PASSWORD), Role.ADMIN));
        String token = data.loginAndGetToken(email, PASSWORD);

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.customer").doesNotExist());
    }

    /** Everything of an error body except what is different on every request. */
    private static Map<String, Object> withoutVolatileFields(MvcResult result) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(
                JsonPath.<Map<String, Object>>read(result.getResponse().getContentAsString(), "$"));
        body.remove("traceId");
        body.remove("timestamp");
        return body;
    }

}
