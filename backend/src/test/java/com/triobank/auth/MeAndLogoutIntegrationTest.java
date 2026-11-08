package com.triobank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.triobank.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Rolled back after each test, so the admin created here does not leak into other tests. */
@Transactional
class MeAndLogoutIntegrationTest extends AbstractIntegrationTest {

    private static final String ME = "/api/v1/me";
    private static final String LOGOUT = "/api/v1/auth/logout";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void TC13_customerSeesProfileWithMaskedPesel() throws Exception {
        RegisteredCustomer customer = registerCustomer();

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.userId().toString()))
                .andExpect(jsonPath("$.email").value("anna.nowak@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.customer.id").value(customer.customerId().toString()))
                .andExpect(jsonPath("$.customer.peselMasked").value("*******2343"))
                .andExpect(jsonPath("$.customer.pesel").doesNotExist())
                .andExpect(jsonPath("$.customer.dateOfBirth").value("1990-05-14"))
                .andExpect(jsonPath("$.customer.firstName").value("Anna"))
                .andExpect(jsonPath("$.customer.phoneNumber").value("+48501234567"))
                .andExpect(jsonPath("$.customer.address.postalCode").value("45-018"))
                .andExpect(jsonPath("$.customer.address.countryCode").value("PL"))
                .andExpect(jsonPath("$.customer.createdAt").isNotEmpty())
                .andExpect(content().string(not(containsString("90051412343"))));
    }

    @Test
    void TC13_adminHasNoCustomer() throws Exception {
        User admin = users.save(new User("admin@triobank.local", "not-used", Role.ADMIN));
        String token = jwtService.issue(admin.getId(), Role.ADMIN, null).value();

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.customer").isEmpty());
    }

    @Test
    void meWithoutTokenGets401() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void TC11_logoutReturns204AndIsAudited() throws Exception {
        RegisteredCustomer customer = registerCustomer();

        mockMvc.perform(post(LOGOUT).header(HttpHeaders.AUTHORIZATION, customer.bearer()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        entityManager.flush();
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_events where action = 'LOGOUT' and actor_user_id = ?",
                Integer.class, customer.userId())).isEqualTo(1);
    }

    @Test
    void logoutWithoutTokenGets401() throws Exception {
        mockMvc.perform(post(LOGOUT)).andExpect(status().isUnauthorized());
    }

    private RegisteredCustomer registerCustomer() throws Exception {
        String body = """
                {
                  "email": "anna.nowak@example.com", "password": "Str0ng!Passw0rd",
                  "firstName": "Anna", "lastName": "Nowak", "pesel": "90051412343", "phoneNumber": "+48501234567",
                  "address": {"street": "Krakowska", "buildingNumber": "12A", "postalCode": "45-018", "city": "Opole"}
                }
                """;
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        UUID userId = UUID.fromString(json.get("userId").asString());
        entityManager.flush();
        UUID customerId = jdbc.queryForObject("select id from customers where user_id = ?", UUID.class, userId);
        String token = jwtService.issue(userId, Role.CUSTOMER, customerId).value();
        return new RegisteredCustomer(userId, customerId, "Bearer " + token);
    }

    private record RegisteredCustomer(UUID userId, UUID customerId, String bearer) {
    }

}
