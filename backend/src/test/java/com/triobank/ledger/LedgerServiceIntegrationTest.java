package com.triobank.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triobank.AbstractIntegrationTest;
import com.triobank.TestDataFactory.RegisteredCustomer;
import com.triobank.common.error.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real postings against PostgreSQL: balances, entries and the ledger invariants after each of them.
 */
class LedgerServiceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID BANK_CASH = UUID.fromString("00000000-0000-4000-8000-000000000201");

    @Autowired
    private LedgerService ledger;

    private RegisteredCustomer customer;
    private UUID accountId;

    @BeforeEach
    void registerCustomer() throws Exception {
        customer = data.registerCustomer();
        accountId = jdbc.queryForObject("""
                SELECT a.id FROM accounts a JOIN customers c ON c.id = a.customer_id WHERE c.user_id = ?
                """, UUID.class, customer.userId());
    }

    @Test
    void depositThenWithdrawalKeepTheLedgerBalanced() {
        Transaction deposit = ledger.post(command(TransactionType.CASH_DEPOSIT, BANK_CASH, accountId, "200.00"));
        Transaction withdrawal = ledger.post(command(TransactionType.CASH_WITHDRAWAL, accountId, BANK_CASH, "50.00"));

        assertThat(balance(accountId)).isEqualByComparingTo("150.00");
        assertThat(entriesOf(accountId)).containsExactly("CREDIT 200.00 200.00", "DEBIT 50.00 150.00");
        assertThat(deposit.getReference()).matches("TB\\d{18}");
        assertThat(withdrawal.getReference()).isNotEqualTo(deposit.getReference());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'TRANSACTION_POSTED' "
                + "AND target_id IN (?, ?)", Integer.class, deposit.getId(), withdrawal.getId())).isEqualTo(2);
        assertLedgerBalanced();
    }

    @Test
    void failedPostingChangesNothing() {
        ledger.post(command(TransactionType.CASH_DEPOSIT, BANK_CASH, accountId, "20.00"));
        BigDecimal cashBefore = balance(BANK_CASH);

        assertThatThrownBy(() -> ledger.post(command(TransactionType.CASH_WITHDRAWAL, accountId, BANK_CASH, "20.01")))
                .isInstanceOf(ApiException.class);

        assertThat(balance(accountId)).isEqualByComparingTo("20.00");
        assertThat(balance(BANK_CASH)).isEqualByComparingTo(cashBefore);
        assertThat(entriesOf(accountId)).hasSize(1);
        assertLedgerBalanced();
    }

    private PostingCommand command(TransactionType type, UUID source, UUID target, String amount) {
        return PostingCommand.builder()
                .type(type)
                .channel(Channel.ATM)
                .sourceAccountId(source)
                .targetAccountId(target)
                .amount(new BigDecimal(amount))
                .currency("PLN")
                .title(type == TransactionType.CASH_DEPOSIT ? "ATM cash deposit" : "ATM cash withdrawal")
                .senderName("Trio Bank")
                .recipientName("Anna Nowak")
                .initiatedByUserId(customer.userId())
                .idempotencyKey(UUID.randomUUID().toString())
                .requestHash("a".repeat(64))
                .build();
    }

    private BigDecimal balance(UUID id) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, id);
    }

    private List<String> entriesOf(UUID id) {
        return jdbc.queryForList("""
                SELECT e.direction || ' ' || e.amount || ' ' || e.balance_after
                FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id
                WHERE e.account_id = ? ORDER BY t.reference
                """, String.class, id);
    }

    /** The three ledger invariants: total is zero, balances match entries, every transaction is balanced. */
    private void assertLedgerBalanced() {
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
