package com.triobank.auth;

import static com.triobank.TestDataFactory.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.triobank.AbstractIntegrationTest;
import com.triobank.TestDataFactory.RegisteredCustomer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * US-03 logout, TC-11 from docs/09-testing.md §5.1. The UI part of TC-11 (redirect to login)
 * belongs to the frontend and e2e tests.
 */
class LogoutQaIntegrationTest extends AbstractIntegrationTest {

    @Test
    void TC11_logout_withValidToken_returns204AndWritesAuditEvent() throws Exception {
        RegisteredCustomer customer = data.registerCustomer();
        String token = data.loginAndGetToken(customer.email(), customer.password());

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(countRows("select count(*) from audit_events where action = 'LOGOUT' and actor_user_id = ?",
                customer.userId())).isEqualTo(1);
    }

    @Test
    void TC11_logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

}
