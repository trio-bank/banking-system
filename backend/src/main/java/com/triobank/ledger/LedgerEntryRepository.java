package com.triobank.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /**
     * Entries of one account, newest first, with the transaction and both of its accounts loaded,
     * so one page is a single select. Entries written at the same moment are ordered by id,
     * so the pages don't overlap. Pass a pageable without sorting: the order is fixed here.
     */
    @Query(value = """
            select e from LedgerEntry e
            join fetch e.transaction t
            join fetch t.sourceAccount
            join fetch t.targetAccount
            where e.account.id = :accountId
            order by e.createdAt desc, e.id desc""",
            countQuery = "select count(e) from LedgerEntry e where e.account.id = :accountId")
    Page<LedgerEntry> findHistoryOfAccount(@Param("accountId") UUID accountId, Pageable pageable);

}
