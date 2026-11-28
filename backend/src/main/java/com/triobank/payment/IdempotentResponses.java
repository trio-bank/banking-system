package com.triobank.payment;

import com.triobank.ledger.idempotency.IdempotentResult;
import com.triobank.payment.dto.TransactionDto;
import java.net.URI;
import org.springframework.http.ResponseEntity;

/**
 * Response of a money operation: 201 with the transaction's address when it was just done,
 * 200 with the original transaction when the same request came again.
 */
final class IdempotentResponses {

    static final String TRANSACTIONS_PATH = "/api/v1/transactions/";

    private IdempotentResponses() {
    }

    static ResponseEntity<TransactionDto> of(IdempotentResult<TransactionDto> result) {
        TransactionDto transaction = result.value();
        if (result.replayed()) {
            return ResponseEntity.ok(transaction);
        }
        return ResponseEntity.created(URI.create(TRANSACTIONS_PATH + transaction.id())).body(transaction);
    }

}
