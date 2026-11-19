package com.triobank.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** The three ledger invariants: total is zero, balances match entries, every transaction is balanced. */
final class LedgerInvariants {

    private LedgerInvariants() {
    }

    static void assertLedgerBalanced(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(balance), 0) FROM accounts", BigDecimal.class))
                .as("sum of all balances").isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForList("""
                SELECT a.id FROM accounts a
                LEFT JOIN ledger_entries e ON e.account_id = a.id
                GROUP BY a.id, a.balance
                HAVING a.balance <> COALESCE(SUM(CASE e.direction WHEN 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
                """, UUID.class)).as("accounts whose balance differs from their entries").isEmpty();
        assertThat(jdbc.queryForList("""
                SELECT t.id FROM transactions t JOIN ledger_entries e ON e.transaction_id = t.id
                GROUP BY t.id, t.amount
                HAVING COUNT(*) <> 2 OR SUM(CASE e.direction WHEN 'CREDIT' THEN e.amount ELSE -e.amount END) <> 0
                    OR MAX(e.amount) <> t.amount
                """, UUID.class)).as("unbalanced transactions").isEmpty();
    }

}
