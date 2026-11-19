package com.triobank.ledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByInitiatedByUserIdAndIdempotencyKey(UUID initiatedByUserId, String idempotencyKey);

    /** Next running number for a transaction reference. */
    @Query(value = "SELECT nextval('transaction_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

}
