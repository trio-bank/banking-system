package com.triobank.ledger;

import com.triobank.common.error.ApiException;
import com.triobank.common.error.ConstraintViolations;
import com.triobank.common.error.ErrorCode;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs a money operation at most once per idempotency key.
 * <ul>
 *   <li>key not used yet: the operation runs;</li>
 *   <li>key used with the same request: the stored transaction is returned and nothing runs again;</li>
 *   <li>key used with a different request: {@code IDEMPOTENCY_KEY_REUSED}.</li>
 * </ul>
 * Two requests with the same key at the same time both pass the first check. The second one then fails
 * on the unique key when it inserts its transaction. PostgreSQL cannot go on inside a transaction after
 * an error, so this class must be called outside of one (from the controller): the failed transaction is
 * already rolled back here, and the stored one is read in a new transaction.
 */
@Component
public class IdempotentExecutor {

    private static final String UNIQUE_KEY = "uk_transactions_idempotency_key";

    private final TransactionRepository transactions;
    private final TransactionTemplate readOnly;

    public IdempotentExecutor(TransactionRepository transactions, PlatformTransactionManager transactionManager) {
        this.transactions = transactions;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * @param request   key and hash of the request
     * @param operation the transactional service call that posts the transaction
     * @param toResult  builds the same response from the stored transaction; runs inside a transaction,
     *                  so it may read lazy fields
     */
    public <T> IdempotentResult<T> execute(IdempotentRequest request, Supplier<T> operation,
                                           Function<Transaction, T> toResult) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Idempotent operations must start outside of a transaction");
        }
        Optional<T> stored = findStored(request, toResult);
        if (stored.isPresent()) {
            return IdempotentResult.replayed(stored.get());
        }
        try {
            return IdempotentResult.created(operation.get());
        } catch (DataIntegrityViolationException ex) {
            if (!ConstraintViolations.isViolationOf(ex, UNIQUE_KEY)) {
                throw ex;
            }
            // another request with the same key was faster
            return findStored(request, toResult).map(IdempotentResult::replayed).orElseThrow(() -> ex);
        }
    }

    private <T> Optional<T> findStored(IdempotentRequest request, Function<Transaction, T> toResult) {
        return readOnly.execute(status -> transactions
                .findByInitiatedByUserIdAndIdempotencyKey(request.userId(), request.key())
                .map(transaction -> {
                    if (!transaction.getRequestHash().equals(request.requestHash())) {
                        throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                                "This idempotency key was already used for a different request.");
                    }
                    return toResult.apply(transaction);
                }));
    }

}
