package com.triobank;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base of the sprint 2 API tests. Everything (app, Testcontainers PostgreSQL, MockMvc, jdbc, clock)
 * comes from {@link AbstractIntegrationTest}; this class only adds the account/ATM helpers ({@link BankApi})
 * and checks the ledger invariants (TC-38) after every subclass.
 */
@ExtendWith(LedgerInvariantExtension.class)
public abstract class ApiTestBase extends AbstractIntegrationTest {

    /** Registers and logs in customers, opens accounts, makes ATM operations. */
    protected BankApi api;

    @BeforeEach
    void createBankApi() {
        api = new BankApi(mockMvc, data);
    }

    /** The API can't block or close an account yet (admin comes later), so tests change the status directly. */
    protected void setAccountStatus(UUID accountId, String status) {
        jdbc.update("UPDATE accounts SET status = ? WHERE id = ?", status, accountId);
    }

    protected BigDecimal bankCashBalance() {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE system_code = 'BANK_CASH'", BigDecimal.class);
    }

    protected int countTransactionsOfUser(UUID userId) {
        return (int) countRows("SELECT count(*) FROM transactions WHERE initiated_by_user_id = ?", userId);
    }

}
