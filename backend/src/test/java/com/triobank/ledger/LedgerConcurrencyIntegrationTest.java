package com.triobank.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triobank.AbstractIntegrationTest;
import com.triobank.account.AccountRepository;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.ledger.idempotency.IdempotentExecutor;
import com.triobank.ledger.idempotency.IdempotentRequest;
import com.triobank.ledger.idempotency.IdempotentResult;
import com.triobank.ledger.idempotency.RequestHash;
import com.triobank.ledger.transaction.Channel;
import com.triobank.ledger.transaction.TransactionType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Account locking and idempotency keys against a real PostgreSQL, with requests running in parallel.
 */
class LedgerConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final UUID BANK_CASH = UUID.fromString("00000000-0000-4000-8000-000000000201");

    @Autowired
    private LedgerService ledger;

    @Autowired
    private IdempotentExecutor idempotent;

    @Autowired
    private AccountRepository accounts;

    private TransactionTemplate inTransaction;

    @Autowired
    void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.inTransaction = new TransactionTemplate(transactionManager);
    }

    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void registerCustomer() throws Exception {
        userId = data.registerCustomer().userId();
        accountId = accountOf(userId);
    }

    @Test
    void replayWithSameKeyReturnsTheSameTransactionAndPostsNothing() {
        IdempotentRequest request = depositRequest(UUID.randomUUID().toString(), "200.00");

        IdempotentResult<UUID> first = deposit(request, "200.00");
        IdempotentResult<UUID> replay = deposit(request, "200.00");

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.value()).isEqualTo(first.value());
        assertThat(balance(accountId)).isEqualByComparingTo("200.00");
        assertThat(transactionsWithKey(request.key())).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentAmountIsRejected() {
        String key = UUID.randomUUID().toString();
        deposit(depositRequest(key, "200.00"), "200.00");

        assertThatThrownBy(() -> deposit(depositRequest(key, "300.00"), "300.00"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThat(balance(accountId)).isEqualByComparingTo("200.00");
    }

    @Test
    void sameKeyForAnotherOperationIsRejected() {
        String key = UUID.randomUUID().toString();
        deposit(depositRequest(key, "50.00"), "50.00");
        IdempotentRequest withdrawal = new IdempotentRequest(userId, key,
                RequestHash.of("ATM_WITHDRAWAL", accountId, new BigDecimal("50.00")));

        assertThatThrownBy(() -> idempotent.execute(withdrawal,
                () -> ledger.post(command(TransactionType.CASH_WITHDRAWAL, accountId, BANK_CASH, "50.00", withdrawal))
                        .getId(),
                Transaction::getId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThat(balance(accountId)).isEqualByComparingTo("50.00");
    }

    @Test
    void parallelRequestsWithSameKeyPostOnce() throws Exception {
        IdempotentRequest request = depositRequest(UUID.randomUUID().toString(), "100.00");

        List<IdempotentResult<UUID>> results = inParallel(10, () -> deposit(request, "100.00"));

        assertThat(results).extracting(IdempotentResult::value).containsOnly(results.getFirst().value());
        assertThat(results).filteredOn(result -> !result.replayed()).hasSize(1);
        assertThat(transactionsWithKey(request.key())).isEqualTo(1);
        assertThat(balance(accountId)).isEqualByComparingTo("100.00");
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    @Test
    void parallelWithdrawalsNeverOverdrawTheAccount() throws Exception {
        post(TransactionType.CASH_DEPOSIT, BANK_CASH, accountId, "100.00");

        List<String> outcomes = inParallel(20, () -> {
            try {
                post(TransactionType.CASH_WITHDRAWAL, accountId, BANK_CASH, "10.00");
                return "OK";
            } catch (ApiException ex) {
                return ex.getCode().name();
            }
        });

        assertThat(outcomes).filteredOn("OK"::equals).hasSize(10);
        assertThat(outcomes).filteredOn(ErrorCode.INSUFFICIENT_FUNDS.name()::equals).hasSize(10);
        assertThat(balance(accountId)).isEqualByComparingTo("0.00");
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    @Test
    void accountReadBeforePostingIsReloadedAfterTheLock() throws Exception {
        post(TransactionType.CASH_DEPOSIT, BANK_CASH, accountId, "100.00");

        // services read the account (ownership, product rules) in the same transaction before posting
        List<String> outcomes = inParallel(20, () -> {
            try {
                return inTransaction.execute(status -> {
                    accounts.findById(accountId).orElseThrow();
                    post(TransactionType.CASH_WITHDRAWAL, accountId, BANK_CASH, "10.00");
                    return "OK";
                });
            } catch (ApiException ex) {
                return ex.getCode().name();
            } catch (OptimisticLockingFailureException ex) {
                return "STALE";
            }
        });

        assertThat(outcomes).filteredOn("OK"::equals).as(outcomes.toString()).hasSize(10);
        assertThat(outcomes).filteredOn(ErrorCode.INSUFFICIENT_FUNDS.name()::equals).hasSize(10);
        assertThat(balance(accountId)).isEqualByComparingTo("0.00");
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    @Test
    void oppositeTransfersInParallelDoNotDeadlock() throws Exception {
        UUID otherAccountId = accountOf(data.registerCustomer().userId());
        post(TransactionType.CASH_DEPOSIT, BANK_CASH, accountId, "100.00");
        post(TransactionType.CASH_DEPOSIT, BANK_CASH, otherAccountId, "100.00");

        List<Callable<UUID>> transfers = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            transfers.add(() -> post(TransactionType.INTERNAL_TRANSFER, accountId, otherAccountId, "1.00").getId());
            transfers.add(() -> post(TransactionType.INTERNAL_TRANSFER, otherAccountId, accountId, "1.00").getId());
        }
        List<UUID> posted = inParallel(transfers);

        assertThat(posted).doesNotHaveDuplicates().hasSize(50);
        assertThat(balance(accountId)).isEqualByComparingTo("100.00");
        assertThat(balance(otherAccountId)).isEqualByComparingTo("100.00");
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    private IdempotentRequest depositRequest(String key, String amount) {
        return new IdempotentRequest(userId, key, RequestHash.of("ATM_DEPOSIT", accountId, new BigDecimal(amount)));
    }

    private IdempotentResult<UUID> deposit(IdempotentRequest request, String amount) {
        return idempotent.execute(request,
                () -> ledger.post(command(TransactionType.CASH_DEPOSIT, BANK_CASH, accountId, amount, request)).getId(),
                Transaction::getId);
    }

    private Transaction post(TransactionType type, UUID source, UUID target, String amount) {
        return ledger.post(command(type, source, target, amount, null));
    }

    private PostingCommand command(TransactionType type, UUID source, UUID target, String amount,
                                   IdempotentRequest request) {
        return PostingCommand.builder()
                .type(type)
                .channel(Channel.ATM)
                .sourceAccountId(source)
                .targetAccountId(target)
                .amount(new BigDecimal(amount))
                .currency("PLN")
                .title(type.name())
                .senderName("Trio Bank")
                .recipientName("Anna Nowak")
                .initiatedByUserId(userId)
                .idempotencyKey(request == null ? null : request.key())
                .requestHash(request == null ? null : request.requestHash())
                .build();
    }

    private static <T> List<T> inParallel(int times, Callable<T> task) throws Exception {
        List<Callable<T>> tasks = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            tasks.add(task);
        }
        return inParallel(tasks);
    }

    /** Starts all tasks at the same moment and waits for every result. */
    private static <T> List<T> inParallel(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private UUID accountOf(UUID customerUserId) {
        return jdbc.queryForObject("""
                SELECT a.id FROM accounts a JOIN customers c ON c.id = a.customer_id WHERE c.user_id = ?
                """, UUID.class, customerUserId);
    }

    private BigDecimal balance(UUID id) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, id);
    }

    private long transactionsWithKey(String key) {
        return countRows("SELECT COUNT(*) FROM transactions WHERE initiated_by_user_id = ? AND idempotency_key = ?",
                userId, key);
    }

}
