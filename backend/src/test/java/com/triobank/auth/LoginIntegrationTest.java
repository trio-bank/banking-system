package com.triobank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.triobank.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Not transactional on purpose: failed attempts must survive the request that failed, so every request commits.
 * Each test registers its own user with a fresh email and PESEL.
 */
@ExtendWith(OutputCaptureExtension.class)
class LoginIntegrationTest extends AbstractIntegrationTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String PASSWORD = "Str0ng!Passw0rd";
    private static final String WRONG = "Wr0ng!Passw0rd";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void TC07_correctCredentialsGiveTokenWithClaims(CapturedOutput output) throws Exception {
        String email = registerCustomer();

        JsonNode first = json(login(email.toUpperCase(), PASSWORD).andExpect(status().isOk()));
        JsonNode second = json(login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.customer").isEmpty()));

        assertThat(first.get("user").get("lastLoginAt").isNull()).isTrue();
        Instant previous = Instant.parse(second.get("user").get("lastLoginAt").asString());
        Instant stored = jdbc.queryForObject("select last_login_at from users where email = ?", Timestamp.class,
                email).toInstant();
        assertThat(stored).isAfter(previous);

        String token = second.get("accessToken").asString();
        Jwt jwt = jwtDecoder.decode(token);
        UUID userId = UUID.fromString(second.get("user").get("id").asString());
        UUID customerId = jdbc.queryForObject("select id from customers where user_id = ?", UUID.class, userId);
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString(JwtService.ROLE_CLAIM)).isEqualTo("CUSTOMER");
        assertThat(jwt.getClaimAsString(JwtService.CUSTOMER_ID_CLAIM)).isEqualTo(customerId.toString());
        assertThat(jwt.getExpiresAt()).isEqualTo(Instant.parse(second.get("expiresAt").asString()));
        assertThat(count("select count(*) from audit_events where action = 'LOGIN_SUCCEEDED' and actor_user_id = ?",
                userId)).isEqualTo(2);

        assertThat(output).doesNotContain(PASSWORD).doesNotContain(token);
    }

    @Test
    void TC08_wrongPasswordAndUnknownEmailGetIdenticalBody(CapturedOutput output) throws Exception {
        String email = registerCustomer();
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";

        JsonNode wrongPassword = json(login(email, WRONG).andExpect(status().isUnauthorized()));
        JsonNode unknownEmail = json(login(unknown, WRONG).andExpect(status().isUnauthorized()));

        assertThat(wrongPassword.get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(withoutRequestSpecificFields(wrongPassword)).isEqualTo(withoutRequestSpecificFields(unknownEmail));
        assertThat(count("select count(*) from audit_events where action = 'LOGIN_FAILED' and actor_email = ?",
                unknown)).isEqualTo(1);
        assertThat(output).doesNotContain(WRONG);
    }

    @Test
    void TC09_fiveWrongPasswordsLockUntilFifteenMinutesPass() throws Exception {
        String email = registerCustomer();
        for (int i = 0; i < 5; i++) {
            login(email, WRONG).andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_LOCKED"))
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty());
        assertThat(jdbc.queryForObject("select status from users where email = ?", String.class, email))
                .isEqualTo("LOCKED");
        assertThat(count("select count(*) from audit_events a join users u on u.id = a.target_id"
                + " where a.action = 'USER_LOCKED' and u.email = ?", email)).isEqualTo(1);

        // Same as the clock moving 15 minutes forward.
        jdbc.update("update users set locked_until = locked_until - interval '15 minutes' where email = ?", email);

        login(email, PASSWORD).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("select status, failed_login_attempts from users where email = ?", email))
                .containsEntry("status", "ACTIVE")
                .containsEntry("failed_login_attempts", 0);
    }

    @Test
    void TC10_blockedUserGets403() throws Exception {
        String email = registerCustomer();
        jdbc.update("update users set status = 'BLOCKED', status_reason = 'test' where email = ?", email);

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    }

    @Test
    void missingPasswordIsValidationError() throws Exception {
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"a@b.pl\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    private String registerCustomer() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        String body = """
                {
                  "email": "%s", "password": "%s", "firstName": "Anna", "lastName": "Nowak",
                  "pesel": "%s", "phoneNumber": "+48501234567",
                  "address": {"street": "Krakowska", "buildingNumber": "12A", "postalCode": "45-018", "city": "Opole"}
                }
                """.formatted(email, PASSWORD, randomAdultPesel());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("email", email, "password", password));
        return mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static JsonNode withoutRequestSpecificFields(JsonNode problem) {
        ObjectNode copy = ((ObjectNode) problem).deepCopy();
        copy.remove("traceId");
        copy.remove("timestamp");
        return copy;
    }

    private Integer count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** A valid PESEL of someone born in 1970–1999, random enough not to clash with other tests. */
    private static String randomAdultPesel() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String digits = "%02d%02d%02d%04d".formatted(random.nextInt(70, 100), random.nextInt(1, 13),
                random.nextInt(1, 29), random.nextInt(10_000));
        int[] weights = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        return digits + (10 - sum % 10) % 10;
    }

}
