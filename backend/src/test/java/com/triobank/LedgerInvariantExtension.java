package com.triobank;

import com.triobank.ledger.LedgerInvariants;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * TC-38 (docs/09-testing.md §4.1): after every integration test class the three ledger invariants
 * must still hold. Registered once on {@link ApiTestBase}, so every subclass gets it.
 */
public class LedgerInvariantExtension implements AfterAllCallback {

    @Override
    public void afterAll(ExtensionContext context) {
        DataSource dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource.class);
        LedgerInvariants.assertLedgerBalanced(new JdbcTemplate(dataSource));
    }

}
