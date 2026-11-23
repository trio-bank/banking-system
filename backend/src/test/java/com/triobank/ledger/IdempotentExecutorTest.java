package com.triobank.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class IdempotentExecutorTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String KEY = "3f2b8c1e-9a4d-4e7f-8b21-5c6d7e8f9a0b";
    private static final String HASH = "a".repeat(64);
    private static final IdempotentRequest REQUEST = new IdempotentRequest(USER_ID, KEY, HASH);

    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final IdempotentExecutor executor =
            new IdempotentExecutor(transactions, mock(PlatformTransactionManager.class));

    private final AtomicInteger runs = new AtomicInteger();

    @Test
    void newKeyRunsTheOperation() {
        givenStored(Optional.empty());

        IdempotentResult<String> result = executor.execute(REQUEST, operation("new"), Transaction::getReference);

        assertThat(result).isEqualTo(new IdempotentResult<>("new", false));
        assertThat(runs).hasValue(1);
    }

    @Test
    void sameKeyAndSameRequestReturnsStoredTransactionWithoutRunning() {
        givenStored(Optional.of(stored(HASH)));

        IdempotentResult<String> result = executor.execute(REQUEST, operation("new"), Transaction::getReference);

        assertThat(result).isEqualTo(new IdempotentResult<>("TB202611190000000001", true));
        assertThat(runs).hasValue(0);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        givenStored(Optional.of(stored("b".repeat(64))));

        assertReused(() -> executor.execute(REQUEST, operation("new"), Transaction::getReference));
        assertThat(runs).hasValue(0);
    }

    @Test
    void lostRaceOnTheKeyReturnsTheWinnersTransaction() {
        when(transactions.findByInitiatedByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty(), Optional.of(stored(HASH)));

        IdempotentResult<String> result = executor.execute(REQUEST,
                () -> { throw violationOf("uk_transactions_idempotency_key"); }, Transaction::getReference);

        assertThat(result).isEqualTo(new IdempotentResult<>("TB202611190000000001", true));
    }

    @Test
    void lostRaceAgainstDifferentRequestIsRejected() {
        when(transactions.findByInitiatedByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.empty(), Optional.of(stored("b".repeat(64))));

        assertReused(() -> executor.execute(REQUEST,
                () -> { throw violationOf("UK_TRANSACTIONS_IDEMPOTENCY_KEY"); }, Transaction::getReference));
    }

    @Test
    void otherConstraintViolationIsNotHidden() {
        givenStored(Optional.empty());
        DataIntegrityViolationException other = violationOf("ck_transactions_amount_positive");

        assertThatThrownBy(() -> executor.execute(REQUEST, () -> { throw other; }, Transaction::getReference))
                .isSameAs(other);
    }

    @Test
    void mustNotRunInsideATransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> executor.execute(REQUEST, operation("new"), Transaction::getReference))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(runs).hasValue(0);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private Supplier<String> operation(String result) {
        return () -> {
            runs.incrementAndGet();
            return result;
        };
    }

    private void givenStored(Optional<Transaction> transaction) {
        when(transactions.findByInitiatedByUserIdAndIdempotencyKey(USER_ID, KEY)).thenReturn(transaction);
    }

    private static Transaction stored(String requestHash) {
        return Transaction.builder()
                .reference("TB202611190000000001")
                .initiatedByUserId(USER_ID)
                .idempotencyKey(KEY)
                .requestHash(requestHash)
                .build();
    }

    private static DataIntegrityViolationException violationOf(String constraint) {
        return new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException("duplicate"), constraint));
    }

    private static void assertReused(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

}
