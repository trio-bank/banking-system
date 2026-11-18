package com.triobank.ledger;

/**
 * All operations run synchronously for now, so every stored transaction is COMPLETED.
 * The other statuses are for scheduled and asynchronous payments later.
 */
public enum TransactionStatus {

    PENDING,
    COMPLETED,
    REJECTED,
    CANCELLED

}
