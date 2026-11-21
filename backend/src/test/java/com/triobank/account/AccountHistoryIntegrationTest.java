package com.triobank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.AbstractIntegrationTest;
import com.triobank.TestDataFactory;
import com.triobank.TestDataFactory.RegisteredCustomer;
import com.triobank.ledger.Channel;
import com.triobank.ledger.LedgerInvariants;
import com.triobank.ledger.LedgerService;
import com.triobank.ledger.PostingCommand;
import com.triobank.ledger.Transaction;
import com.triobank.ledger.TransactionType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * GET /api/v1/accounts/{id}/history against a real database. Cash goes through the ATM endpoints;
 * the transfer to another customer is posted to the ledger directly, because there is no transfer endpoint yet.
 */
class AccountHistoryIntegrationTest extends AbstractIntegrationTest {

    private static final String ACCOUNTS = "/api/v1/accounts";

    @Autowired
    private LedgerService ledger;

    private RegisteredCustomer anna;
    private String annaBearer;
    private UUID annaAccount;
    private RegisteredCustomer jan;
    private UUID janAccount;

    @BeforeEach
    void registerCustomers() throws Exception {
        anna = data.registerCustomer();
        annaBearer = TestDataFactory.bearer(data.loginAndGetToken(anna.email(), anna.password()));
        annaAccount = accountId(anna.iban());
        jan = data.registerCustomer();
        janAccount = accountId(jan.iban());
    }

    @AfterEach
    void ledgerStaysBalanced() {
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    @Test
    void TC32_historyIsNewestFirstWithBalanceAndCounterparty() throws Exception {
        atm("deposits", "500.00");
        clock.advance(Duration.ofMinutes(1));
        atm("withdrawals", "100.00");
        clock.advance(Duration.ofMinutes(1));
        Transaction transfer = transferToJan("150.00");

        history(annaAccount, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(3))

                .andExpect(jsonPath("$.content[0].transactionId").value(transfer.getId().toString()))
                .andExpect(jsonPath("$.content[0].reference").value(transfer.getReference()))
                .andExpect(jsonPath("$.content[0].type").value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.content[0].channel").value("WEB"))
                .andExpect(jsonPath("$.content[0].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.content[0].amount").value("150.00"))
                .andExpect(jsonPath("$.content[0].currency").value("PLN"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value("250.00"))
                .andExpect(jsonPath("$.content[0].title").value("Dinner"))
                .andExpect(jsonPath("$.content[0].counterpartyName").value("Jan Kowalski"))
                .andExpect(jsonPath("$.content[0].counterpartyIban").value(jan.iban()))
                .andExpect(jsonPath("$.content[0].entryId").isString())
                .andExpect(jsonPath("$.content[0].createdAt").isString())
                .andExpect(jsonPath("$.content[0].bookedAt").isString())

                .andExpect(jsonPath("$.content[1].type").value("CASH_WITHDRAWAL"))
                .andExpect(jsonPath("$.content[1].channel").value("ATM"))
                .andExpect(jsonPath("$.content[1].direction").value("OUTGOING"))
                .andExpect(jsonPath("$.content[1].amount").value("100.00"))
                .andExpect(jsonPath("$.content[1].balanceAfter").value("400.00"))
                .andExpect(jsonPath("$.content[1].title").value("ATM cash withdrawal"))
                .andExpect(jsonPath("$.content[1].counterpartyName").value("Trio Bank"))
                .andExpect(jsonPath("$.content[1].counterpartyIban").value((Object) null))

                .andExpect(jsonPath("$.content[2].type").value("CASH_DEPOSIT"))
                .andExpect(jsonPath("$.content[2].direction").value("INCOMING"))
                .andExpect(jsonPath("$.content[2].amount").value("500.00"))
                .andExpect(jsonPath("$.content[2].balanceAfter").value("500.00"))
                .andExpect(jsonPath("$.content[2].counterpartyName").value("Trio Bank"))
                .andExpect(jsonPath("$.content[2].counterpartyIban").value((Object) null));
    }

    @Test
    void TC32_recipientSeesTheTransferAsIncomingFromTheSender() throws Exception {
        atm("deposits", "500.00");
        Transaction transfer = transferToJan("150.00");
        String janBearer = TestDataFactory.bearer(data.loginAndGetToken(jan.email(), jan.password()));

        mockMvc.perform(get(ACCOUNTS + "/" + janAccount + "/history").header(HttpHeaders.AUTHORIZATION, janBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionId").value(transfer.getId().toString()))
                .andExpect(jsonPath("$.content[0].direction").value("INCOMING"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value("150.00"))
                .andExpect(jsonPath("$.content[0].counterpartyName").value("Anna Nowak"))
                .andExpect(jsonPath("$.content[0].counterpartyIban").value(anna.iban()));
    }

    @Test
    void TC32_pagesFollowEachOtherWithoutGapsOrRepeats() throws Exception {
        for (int i = 1; i <= 5; i++) {
            atm("deposits", i + "0.00");
            clock.advance(Duration.ofMinutes(1));
        }

        List<String> amounts = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String body = history(annaAccount, "?page=" + page + "&size=2")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(2))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andReturn().getResponse().getContentAsString();
            amounts.addAll(JsonPath.read(body, "$.content[*].amount"));
        }

        assertThat(amounts).containsExactly("50.00", "40.00", "30.00", "20.00", "10.00");
        history(annaAccount, "?page=3&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void accountWithoutOperationsHasEmptyHistory() throws Exception {
        history(annaAccount, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void pageAndSizeOutOfRangeAreRejected() throws Exception {
        for (String query : new String[] {"?size=101", "?size=0", "?page=-1"}) {
            String field = query.substring(1, query.indexOf('='));
            history(annaAccount, query)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value(field))
                    .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
        }
        history(annaAccount, "?size=100").andExpect(status().isOk());
        history(annaAccount, "?size=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void anotherCustomersAccountLooksMissing() throws Exception {
        atm("deposits", "500.00");
        transferToJan("150.00");

        for (UUID id : new UUID[] {janAccount, UUID.randomUUID()}) {
            history(id, "")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value("Account not found."));
        }
    }

    @Test
    void historyQueryUsesTheAccountIndex() {
        // Same shape as the query Hibernate runs for a page. Sequential scans are switched off,
        // because on a tiny test table Postgres would rather read the whole table.
        String plan = jdbc.execute((ConnectionCallback<String>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                StringBuilder lines = new StringBuilder();
                try (ResultSet rows = statement.executeQuery("""
                        EXPLAIN SELECT e.id FROM ledger_entries e
                        JOIN transactions t ON t.id = e.transaction_id
                        WHERE e.account_id = '%s'
                        ORDER BY e.created_at DESC, e.id DESC LIMIT 20""".formatted(annaAccount))) {
                    while (rows.next()) {
                        lines.append(rows.getString(1)).append('\n');
                    }
                }
                statement.execute("RESET enable_seqscan");
                return lines.toString();
            }
        });

        assertThat(plan).contains("idx_ledger_entries_account_created_at");
    }

    private Transaction transferToJan(String amount) {
        return ledger.post(PostingCommand.builder()
                .type(TransactionType.INTERNAL_TRANSFER)
                .channel(Channel.WEB)
                .sourceAccountId(annaAccount)
                .targetAccountId(janAccount)
                .amount(new BigDecimal(amount))
                .currency("PLN")
                .title("Dinner")
                .senderName("Anna Nowak")
                .recipientName("Jan Kowalski")
                .initiatedByUserId(anna.userId())
                .build());
    }

    private void atm(String operation, String amount) throws Exception {
        mockMvc.perform(post("/api/v1/atm/" + operation)
                        .header(HttpHeaders.AUTHORIZATION, annaBearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": \"" + annaAccount + "\", \"amount\": \"" + amount + "\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions history(UUID accountId, String query) throws Exception {
        return mockMvc.perform(get(ACCOUNTS + "/" + accountId + "/history" + query)
                .header(HttpHeaders.AUTHORIZATION, annaBearer));
    }

    private UUID accountId(String iban) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE iban = ?", UUID.class, iban);
    }

}
