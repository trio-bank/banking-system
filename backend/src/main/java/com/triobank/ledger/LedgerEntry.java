package com.triobank.ledger;

import com.triobank.account.Account;
import com.triobank.ledger.transaction.EntryDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * One side of a transaction on one account. Every transaction has exactly two: a debit on the source
 * and a credit on the target. Account history is read from these rows. Never changed once written.
 */
@Getter
@Entity
@Immutable
@Table(name = "ledger_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EntryDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** Account balance right after this entry. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private Instant createdAt;

    /** Lets {@code save()} insert directly instead of selecting first, because the id is set by us. */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean persisted;

    static LedgerEntry of(Transaction transaction, Account account, EntryDirection direction, BigDecimal amount,
                          BigDecimal balanceAfter, Instant createdAt) {
        LedgerEntry entry = new LedgerEntry();
        entry.id = UUID.randomUUID();
        entry.transaction = transaction;
        entry.account = account;
        entry.direction = direction;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.createdAt = createdAt;
        return entry;
    }

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        return id.equals(((LedgerEntry) other).getId());
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

}
