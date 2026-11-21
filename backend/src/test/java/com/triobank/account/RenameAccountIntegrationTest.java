package com.triobank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.triobank.AbstractIntegrationTest;
import com.triobank.TestDataFactory;
import com.triobank.TestDataFactory.RegisteredCustomer;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * PATCH /api/v1/accounts/{id} against a real database.
 */
class RenameAccountIntegrationTest extends AbstractIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";

    private RegisteredCustomer anna;
    private String annaBearer;
    private UUID annaAccountId;

    @BeforeEach
    void registerCustomer() throws Exception {
        anna = data.registerCustomer();
        annaBearer = TestDataFactory.bearer(data.loginAndGetToken(anna.email(), anna.password()));
        annaAccountId = jdbc.queryForObject("SELECT id FROM accounts WHERE iban = ?", UUID.class, anna.iban());
    }

    @Test
    void TC19_validNameIsTrimmedSavedAndAudited() throws Exception {
        rename(annaAccountId, "{\"name\": \"  Daily expenses  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(annaAccountId.toString()))
                .andExpect(jsonPath("$.name").value("Daily expenses"))
                .andExpect(jsonPath("$.iban").value(anna.iban()));

        mockMvc.perform(get(ACCOUNTS + "/" + annaAccountId).header(HttpHeaders.AUTHORIZATION, annaBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Daily expenses"));
        mockMvc.perform(get(ACCOUNTS).header(HttpHeaders.AUTHORIZATION, annaBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Daily expenses"));

        assertThat(jdbc.queryForObject("""
                SELECT details ->> 'oldName' || ' -> ' || (details ->> 'newName') FROM audit_events
                WHERE action = 'ACCOUNT_RENAMED' AND target_id = ?""", String.class, annaAccountId))
                .isEqualTo("Trio Personal Account -> Daily expenses");
    }

    @Test
    void TC19_nameOfFiftyOneCharactersIsRejected() throws Exception {
        rename(annaAccountId, "{\"name\": \"" + "a".repeat(51) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));

        rename(annaAccountId, "{\"name\": \" " + "a".repeat(50) + " \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("a".repeat(50)));
    }

    @Test
    void blankOrMissingNameIsRequired() throws Exception {
        for (String body : new String[] {"{\"name\": \"   \"}", "{}"}) {
            rename(annaAccountId, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        }
    }

    @Test
    void blockedAccountCannotBeRenamed() throws Exception {
        jdbc.update("UPDATE accounts SET status = 'BLOCKED' WHERE id = ?", annaAccountId);

        rename(annaAccountId, "{\"name\": \"Daily expenses\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERATION_NOT_ALLOWED_FOR_ACCOUNT"))
                .andExpect(jsonPath("$.detail").value("Only an active account can be renamed."));

        assertThat(jdbc.queryForObject("SELECT name FROM accounts WHERE id = ?", String.class, annaAccountId))
                .isEqualTo("Trio Personal Account");
        assertThat(countRows("SELECT count(*) FROM audit_events WHERE action = 'ACCOUNT_RENAMED' AND target_id = ?",
                annaAccountId)).isZero();
    }

    @Test
    void anotherCustomersAccountLooksMissing() throws Exception {
        RegisteredCustomer other = data.registerCustomer();
        UUID otherAccountId = jdbc.queryForObject("SELECT id FROM accounts WHERE iban = ?", UUID.class,
                other.iban());

        for (UUID id : new UUID[] {otherAccountId, UUID.randomUUID()}) {
            rename(id, "{\"name\": \"Mine now\"}")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        assertThat(jdbc.queryForObject("SELECT name FROM accounts WHERE id = ?", String.class, otherAccountId))
                .isEqualTo("Trio Personal Account");
    }

    private ResultActions rename(UUID accountId, String body) throws Exception {
        return mockMvc.perform(patch(ACCOUNTS + "/" + accountId)
                .header(HttpHeaders.AUTHORIZATION, annaBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

}
