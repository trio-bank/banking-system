package com.triobank.ledger;

import com.triobank.account.Account;
import com.triobank.ledger.transaction.Channel;
import com.triobank.ledger.transaction.TransactionStatus;
import com.triobank.ledger.transaction.TransactionType;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * One money movement from a source account to a target account. Once written, it is never changed;
 * balances and the two ledger entries are written in the same database transaction.
 */
@Getter
@Entity
@Immutable
@Table(name = "transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, length = 24)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_account_id", nullable = false)
    private Account targetAccount;

    @Column(nullable = false, length = 140)
    private String senderName;

    @Column(nullable = false, length = 140)
    private String recipientName;

    @Column(nullable = false, length = 140)
    private String title;

    /** Customer or admin who started the operation; null for scheduled jobs. */
    private UUID initiatedByUserId;

    @Column(length = 64)
    private String idempotencyKey;

    /** SHA-256 of the request, hex. Tells a replay of the same request from a reused key. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String requestHash;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant bookedAt;

    /** Lets {@code save()} insert directly instead of selecting first, because the id is set by us. */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean persisted;

    @Builder(access = AccessLevel.PACKAGE)
    private Transaction(String reference, TransactionType type, TransactionStatus status, Channel channel,
                        BigDecimal amount, String currency, Account sourceAccount, Account targetAccount,
                        String senderName, String recipientName, String title, UUID initiatedByUserId,
                        String idempotencyKey, String requestHash, Instant createdAt, Instant bookedAt) {
        this.id = UUID.randomUUID();
        this.reference = reference;
        this.type = type;
        this.status = status;
        this.channel = channel;
        this.amount = amount;
        this.currency = currency;
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.senderName = senderName;
        this.recipientName = recipientName;
        this.title = title;
        this.initiatedByUserId = initiatedByUserId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
        this.bookedAt = bookedAt;
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
        return id.equals(((Transaction) other).getId());
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

}
