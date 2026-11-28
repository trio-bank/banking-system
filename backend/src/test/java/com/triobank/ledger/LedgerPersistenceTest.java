package com.triobank.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.triobank.IntegrationTest;
import com.triobank.account.Account;
import com.triobank.account.AccountRepository;
import com.triobank.account.SystemAccountCode;
import com.triobank.ledger.transaction.Channel;
import com.triobank.ledger.transaction.EntryDirection;
import com.triobank.ledger.transaction.TransactionStatus;
import com.triobank.ledger.transaction.TransactionType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactions and ledger entries are stored and loaded through JPA, and the table constraints
 * from migration V005 reject broken rows.
 */
@IntegrationTest
@Transactional
class LedgerPersistenceTest {

    private static final UUID CASH_ID = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID INTEREST_ID = UUID.fromString("00000000-0000-4000-8000-000000000202");
    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-18T10:15:30Z");

    private static final String INSERT_TRANSACTION = """
            INSERT INTO transactions (id, reference, type, status, channel, amount, currency, source_account_id,
                                      target_account_id, sender_name, recipient_name, title, initiated_by_user_id,
                                      idempotency_key, request_hash, created_at, booked_at)
            VALUES (?, ?, ?, 'COMPLETED', 'ATM', ?, 'PLN', ?, ?, 'Trio Bank', 'Anna Nowak', 'Test', ?, ?, ?, NOW(), NOW())
            """;

    private static final String INSERT_ENTRY = """
            INSERT INTO ledger_entries (id, transaction_id, account_id, direction, amount, balance_after, created_at)
            VALUES (?, ?, ?, ?, ?, 0.00, NOW())
            """;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private LedgerEntryRepository entries;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Account cash;
    private Account interest;

    @BeforeEach
    void loadSystemAccounts() {
        cash = accounts.findBySystemCode(SystemAccountCode.BANK_CASH).orElseThrow();
        interest = accounts.findBySystemCode(SystemAccountCode.BANK_INTEREST_EXPENSE).orElseThrow();
    }

    @Test
    void transactionWithTwoEntriesIsStoredAndLoaded() {
        Transaction saved = transactions.save(Transaction.builder()
                .reference("TB202611180000000001")
                .type(TransactionType.INTEREST)
                .status(TransactionStatus.COMPLETED)
                .channel(Channel.SYSTEM)
                .amount(new BigDecimal("12.34"))
                .currency("PLN")
                .sourceAccount(interest)
                .targetAccount(cash)
                .senderName("Trio Bank")
                .recipientName("Trio Bank")
                .title("Interest capitalization 2026-10")
                .createdAt(NOW)
                .bookedAt(NOW)
                .build());
        entries.save(LedgerEntry.of(saved, interest, EntryDirection.DEBIT, new BigDecimal("12.34"),
                new BigDecimal("-12.34"), NOW));
        entries.save(LedgerEntry.of(saved, cash, EntryDirection.CREDIT, new BigDecimal("12.34"),
                new BigDecimal("12.34"), NOW));
        entityManager.flush();
        entityManager.clear();

        Transaction loaded = transactions.findById(saved.getId()).orElseThrow();
        assertThat(loaded.isNew()).isFalse();
        assertThat(loaded.getReference()).isEqualTo("TB202611180000000001");
        assertThat(loaded.getType()).isEqualTo(TransactionType.INTEREST);
        assertThat(loaded.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(loaded.getChannel()).isEqualTo(Channel.SYSTEM);
        assertThat(loaded.getAmount()).isEqualByComparingTo("12.34");
        assertThat(loaded.getCurrency()).isEqualTo("PLN");
        assertThat(loaded.getSourceAccount().getId()).isEqualTo(INTEREST_ID);
        assertThat(loaded.getTargetAccount().getId()).isEqualTo(CASH_ID);
        assertThat(loaded.getInitiatedByUserId()).isNull();
        assertThat(loaded.getIdempotencyKey()).isNull();
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(loaded.getBookedAt()).isEqualTo(NOW);

        assertThat(entries.findByTransactionId(saved.getId()))
                .extracting(LedgerEntry::getDirection, entry -> entry.getAccount().getId(),
                        entry -> entry.getBalanceAfter().toPlainString())
                .containsExactlyInAnyOrder(
                        tuple(EntryDirection.DEBIT, INTEREST_ID, "-12.34"),
                        tuple(EntryDirection.CREDIT, CASH_ID, "12.34"));
    }

    @Test
    void transactionWithZeroAmountIsRejected() {
        assertThatThrownBy(() -> insertTransaction(BigDecimal.ZERO, INTEREST_ID, CASH_ID, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_amount_positive");
    }

    @Test
    void transactionWithSameSourceAndTargetIsRejected() {
        assertThatThrownBy(() -> insertTransaction(BigDecimal.TEN, CASH_ID, CASH_ID, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_different_accounts");
    }

    @Test
    void unknownTransactionTypeIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT_TRANSACTION, UUID.randomUUID(), uniqueReference(), "GIFT",
                BigDecimal.TEN, INTEREST_ID, CASH_ID, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_type");
    }

    @Test
    void sameIdempotencyKeyOfOneUserIsRejected() {
        UUID userId = insertUser();
        insertTransaction(BigDecimal.TEN, INTEREST_ID, CASH_ID, userId, "key-1", HASH);

        assertThatThrownBy(() -> insertTransaction(BigDecimal.TEN, INTEREST_ID, CASH_ID, userId, "key-1", HASH))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_transactions_idempotency_key");
    }

    @Test
    void sameIdempotencyKeyOfDifferentUsersIsAllowed() {
        insertTransaction(BigDecimal.TEN, INTEREST_ID, CASH_ID, insertUser(), "key-1", HASH);
        insertTransaction(BigDecimal.TEN, INTEREST_ID, CASH_ID, insertUser(), "key-1", HASH);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE idempotency_key = 'key-1'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void idempotencyKeyWithoutRequestHashIsRejected() {
        UUID userId = insertUser();

        assertThatThrownBy(() -> insertTransaction(BigDecimal.TEN, INTEREST_ID, CASH_ID, userId, "key-1", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transactions_idempotency_hash");
    }

    @Test
    void ledgerEntryWithZeroAmountIsRejected() {
        UUID transactionId = insertTransaction(BigDecimal.TEN, INTEREST_ID, CASH_ID, null, null, null);

        assertThatThrownBy(() -> jdbc.update(INSERT_ENTRY, UUID.randomUUID(), transactionId, CASH_ID, "CREDIT",
                BigDecimal.ZERO))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ledger_entries_amount_positive");
    }

    @Test
    void secondDebitForOneTransactionIsRejected() {
        UUID transactionId = insertTransaction(BigDecimal.TEN, INTEREST_ID, CASH_ID, null, null, null);
        jdbc.update(INSERT_ENTRY, UUID.randomUUID(), transactionId, INTEREST_ID, "DEBIT", BigDecimal.TEN);

        assertThatThrownBy(() -> jdbc.update(INSERT_ENTRY, UUID.randomUUID(), transactionId, CASH_ID, "DEBIT",
                BigDecimal.TEN))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_ledger_entries_transaction_direction");
    }

    @Test
    void historyIndexExists() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_ledger_entries_account_created_at'",
                String.class);

        assertThat(definition).contains("(account_id, created_at DESC)");
    }

    @Test
    void referenceSequenceStartsAtOneAndFitsTenDigits() {
        assertThat(jdbc.queryForMap(
                "SELECT start_value, max_value FROM pg_sequences WHERE sequencename = 'transaction_reference_seq'"))
                .containsEntry("start_value", 1L)
                .containsEntry("max_value", 9_999_999_999L);
    }

    private UUID insertTransaction(BigDecimal amount, UUID sourceId, UUID targetId, UUID userId,
                                   String idempotencyKey, String requestHash) {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT_TRANSACTION, id, uniqueReference(), "CASH_DEPOSIT", amount, sourceId, targetId, userId,
                idempotencyKey, requestHash);
        return id;
    }

    private static String uniqueReference() {
        return "TB" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, created_at, updated_at)
                VALUES (?, ?, 'hash', 'CUSTOMER', NOW(), NOW())
                """, userId, userId + "@example.com");
        return userId;
    }

}
