package com.triobank.ledger;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TransactionRepository extends JpaRepository<Transaction, UUID> {
}
