package com.triobank.auth;

import static com.triobank.TestDataFactory.bearer;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** US-04 session timer, TC-12 from docs/09-testing.md §5.1. The "session expired" message is a UI matter. */
class ExpiredTokenQaIntegrationTest extends AbstractIntegrationTest {

    @Test
    void TC12_expiredToken_returns401Unauthenticated() throws Exception {
        RegisteredCustomer customer = data.registerCustomer();

        // a token issued two hours ago is long expired (it lives 30 minutes)
        clock.setOffset(Duration.ofHours(-2));
        String expiredToken = data.loginAndGetToken(customer.email(), customer.password());
        clock.reset();
        assertThat(expiresAt(expiredToken)).isBefore(Instant.now());

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // control: a fresh token works, so the 401 above is really about the expiry
        String freshToken = data.loginAndGetToken(customer.email(), customer.password());
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(freshToken)))
                .andExpect(status().isOk());
    }

    private static Instant expiresAt(String token) {
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return Instant.ofEpochSecond(JsonPath.<Number>read(claims, "$.exp").longValue());
    }

}
