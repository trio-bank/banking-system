package com.triobank.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.triobank.AbstractIntegrationTest;
import com.triobank.common.iban.IbanValidator;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Each test runs in a transaction that is rolled back, so emails and PESELs can be reused between tests. */
@Transactional
class RegistrationIntegrationTest extends AbstractIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String PASSWORD = "Str0ng!Passw0rd";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void TC01_registersCustomerWithPersonalAccount() throws Exception {
        MvcResult result = register(body("Anna.Nowak@Example.com", "90051412343"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/me"))
                .andExpect(jsonPath("$.email").value("anna.nowak@example.com"))
                .andExpect(jsonPath("$.customerNumber").value(matchesPattern("\\d{8}")))
                .andExpect(jsonPath("$.account.type").value("PERSONAL"))
                .andExpect(jsonPath("$.account.productCode").value("PERSONAL_STANDARD"))
                .andExpect(jsonPath("$.account.name").value("Trio Personal Account"))
                .andExpect(jsonPath("$.account.currency").value("PLN"))
                .andExpect(jsonPath("$.account.balance").value("0.00"))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.account.interestRate").value("0.0000"))
                .andExpect(jsonPath("$.account.accruedInterest").doesNotExist())
                .andExpect(jsonPath("$.account.openedAt").isNotEmpty())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID userId = UUID.fromString(response.get("userId").asString());
        String iban = response.get("account").get("iban").asString();
        assertThat(iban).startsWith("PL").hasSize(28);
        assertThat(IbanValidator.isValid(iban)).isTrue();

        entityManager.flush();
        Map<String, Object> user = jdbc.queryForMap("select role, status from users where id = ?", userId);
        assertThat(user).containsEntry("role", "CUSTOMER").containsEntry("status", "ACTIVE");
        Map<String, Object> customer = jdbc.queryForMap(
                "select date_of_birth, address_country_code from customers where user_id = ?", userId);
        assertThat(customer.get("date_of_birth")).hasToString("1990-05-14");
        assertThat(customer.get("address_country_code")).isEqualTo("PL");
        assertThat(count("select count(*) from accounts a join customers c on c.id = a.customer_id"
                + " where c.user_id = ?", userId)).isEqualTo(1);

        assertThat(count("select count(*) from audit_events where action = 'USER_REGISTERED' and target_id = ?",
                userId)).isEqualTo(1);
        assertThat(count("select count(*) from audit_events a join accounts acc on acc.id = a.target_id"
                + " where a.action = 'ACCOUNT_OPENED' and acc.iban = ?", iban)).isEqualTo(1);
        assertThat(count("select count(*) from audit_events where actor_user_id = ? and details::text like ?",
                userId, "%" + PASSWORD + "%")).isZero();
    }

    @Test
    void TC02_sameEmailInOtherCaseIsRejected() throws Exception {
        register(body("anna.nowak@example.com", "90051412343")).andExpect(status().isCreated());

        expectError(register(body("ANNA.Nowak@example.com", "85110356782")), 409, "EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void samePeselIsRejected() throws Exception {
        register(body("anna.nowak@example.com", "90051412343")).andExpect(status().isCreated());

        expectError(register(body("other@example.com", "90051412343")), 409, "PESEL_ALREADY_REGISTERED");
    }

    @Test
    void TC03_invalidPeselChecksumIsRejected() throws Exception {
        expectFieldError(register(body("anna.nowak@example.com", "90051412344")), "pesel", "INVALID_CHECKSUM");
    }

    @Test
    void TC04_underageCustomerIsRejected() throws Exception {
        expectError(register(body("young@example.com", "10270111119")), 422, "CUSTOMER_UNDERAGE");
        assertThat(count("select count(*) from users where email = ?", "young@example.com")).isZero();
    }

    @Test
    void TC05_weakPasswordIsRejected() throws Exception {
        String weak = body("anna.nowak@example.com", "90051412343").replace(PASSWORD, "password");

        expectFieldError(register(weak), "password", "WEAK_PASSWORD");
    }

    @Test
    void passwordWithEmailNameIsRejected() throws Exception {
        String request = body("anna.nowak@example.com", "90051412343").replace(PASSWORD, "Anna.Nowak!2026");

        expectFieldError(register(request), "password", "WEAK_PASSWORD");
    }

    @Test
    void TC06_roleInBodyIsIgnored() throws Exception {
        String request = body("anna.nowak@example.com", "90051412343").replaceFirst("\\{", "{\"role\": \"ADMIN\",");

        MvcResult result = register(request).andExpect(status().isCreated()).andReturn();

        String userId = objectMapper.readTree(result.getResponse().getContentAsString()).get("userId").asString();
        assertThat(jdbc.queryForObject("select role from users where id = ?", String.class, UUID.fromString(userId)))
                .isEqualTo("CUSTOMER");
    }

    @Test
    void polishPostalCodeMustHaveDash() throws Exception {
        String request = body("anna.nowak@example.com", "90051412343").replace("45-018", "45018");

        expectFieldError(register(request), "address.postalCode", "INVALID_FORMAT");
    }

    @Test
    void foreignPostalCodeIsAccepted() throws Exception {
        String request = body("anna.nowak@example.com", "90051412343")
                .replace("45-018", "10115").replace("\"countryCode\": null", "\"countryCode\": \"DE\"");

        register(request).andExpect(status().isCreated());
    }

    @Test
    void missingFieldsAreReported() throws Exception {
        register("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("address")));
    }

    private ResultActions register(String json) throws Exception {
        return mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static void expectError(ResultActions actions, int status, String code) throws Exception {
        actions.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private static void expectFieldError(ResultActions actions, String field, String code) throws Exception {
        actions.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value(code));
    }

    private Integer count(String sql, Object... args) {
        entityManager.flush();
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static String body(String email, String pesel) {
        return """
                {
                  "email": "%s",
                  "password": "%s",
                  "firstName": "Anna",
                  "lastName": "Nowak",
                  "pesel": "%s",
                  "phoneNumber": "+48501234567",
                  "address": {
                    "street": "Krakowska",
                    "buildingNumber": "12A",
                    "apartmentNumber": "4",
                    "postalCode": "45-018",
                    "city": "Opole",
                    "countryCode": null
                  }
                }
                """.formatted(email, PASSWORD, pesel);
    }

}
