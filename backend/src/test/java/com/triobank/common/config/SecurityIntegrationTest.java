package com.triobank.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.triobank.AbstractIntegrationTest;
import com.triobank.auth.JwtProperties;
import com.triobank.auth.JwtService;
import com.triobank.auth.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String ME = "/api/v1/test-security/me";
    private static final String ADMIN_PING = "/api/v1/admin/test-security/ping";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void requestWithoutTokenGets401ProblemDetails() throws Exception {
        expectUnauthenticated(mockMvc.perform(get(ME)))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.instance").value(ME))
                .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{16}")));
    }

    @Test
    void garbageTokenGets401() throws Exception {
        expectUnauthenticated(mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")));
    }

    @Test
    void TC42_tamperedTokenGets401() throws Exception {
        String token = customerToken();
        // swap the role in the payload while keeping the original signature
        String[] parts = token.split("\\.");
        String adminToken = jwtService.issue(USER_ID, Role.ADMIN, null).value();
        String tampered = parts[0] + "." + adminToken.split("\\.")[1] + "." + parts[2];

        expectUnauthenticated(mockMvc.perform(get(ADMIN_PING).header(HttpHeaders.AUTHORIZATION, bearer(tampered))));
    }

    @Test
    void TC42_tokenSignedWithAnotherKeyGets401() throws Exception {
        JwtEncoder foreignEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
                "some-other-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        String token = new JwtService(foreignEncoder, jwtProperties, Clock.systemUTC())
                .issue(USER_ID, Role.ADMIN, null).value();

        expectUnauthenticated(mockMvc.perform(get(ADMIN_PING).header(HttpHeaders.AUTHORIZATION, bearer(token))));
    }

    @Test
    void TC42_expiredTokenGets401() throws Exception {
        Clock longAgo = Clock.fixed(Instant.now().minus(Duration.ofMinutes(31)), ZoneOffset.UTC);
        String token = new JwtService(jwtEncoder, jwtProperties, longAgo).issue(USER_ID, Role.ADMIN, null).value();

        expectUnauthenticated(mockMvc.perform(get(ADMIN_PING).header(HttpHeaders.AUTHORIZATION, bearer(token))));
    }

    @Test
    void TC41_customerOnAdminEndpointGets403() throws Exception {
        mockMvc.perform(get(ADMIN_PING).header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.instance").value(ADMIN_PING));
    }

    @Test
    void adminOnAdminEndpointGets200() throws Exception {
        mockMvc.perform(get(ADMIN_PING).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void currentUserOfCustomerComesFromToken() throws Exception {
        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(customerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()));
    }

    @Test
    void currentUserOfAdminHasNoCustomerId() throws Exception {
        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.customerId").isEmpty());
    }

    @Test
    void registerAndLoginNeedNoToken() throws Exception {
        // the endpoints don't exist yet; anything but 401 means security let the request through
        for (String path : new String[] {"/api/v1/auth/register", "/api/v1/auth/login"}) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().is(not(401)));
        }
    }

    @Test
    void otherAuthEndpointsNeedToken() throws Exception {
        expectUnauthenticated(mockMvc.perform(get("/api/v1/auth/me")));
    }

    @Test
    void preflightFromFrontendIsAllowed() throws Exception {
        mockMvc.perform(options(ME)
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PATCH"));
    }

    @Test
    void corsExposesTraceIdAndLocation() throws Exception {
        mockMvc.perform(get(ME)
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Trace-Id, Location"));
    }

    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options(ME)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void deleteIsNotAllowedByCors() throws Exception {
        mockMvc.perform(options(ME)
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordsAreHashedWithBcryptCost12() {
        assertThat(passwordEncoder.encode("Secret123!")).startsWith("$2a$12$");
    }

    private ResultActions expectUnauthenticated(ResultActions result) throws Exception {
        return result
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    private String customerToken() {
        return jwtService.issue(USER_ID, Role.CUSTOMER, CUSTOMER_ID).value();
    }

    private String adminToken() {
        return jwtService.issue(USER_ID, Role.ADMIN, null).value();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

}
