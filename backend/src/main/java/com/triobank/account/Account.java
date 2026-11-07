package com.triobank.account;

import com.triobank.common.money.MoneyConstants;
import com.triobank.common.persistence.BaseEntity;
import com.triobank.customer.Customer;
import com.triobank.product.AccountProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A bank account. It either belongs to a customer or is one of the bank's own system accounts.
 */
@Getter
@Entity
@Table(name = "accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    @Column(nullable = false, length = 34, updatable = false)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AccountCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", updatable = false)
    private AccountProduct product;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, updatable = false)
    private SystemAccountCode systemCode;

    @Column(nullable = false, length = 50)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(MoneyConstants.SCALE);

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal accruedInterest = BigDecimal.ZERO.setScale(MoneyConstants.ACCRUAL_SCALE);

    private LocalDate lastAccrualDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(length = 255)
    private String statusReason;

    @Column(nullable = false, updatable = false)
    private Instant openedAt;

    /**
     * Opens a customer account with zero balance. Business rules (limits, prerequisites) are checked by the caller.
     */
    public static Account openForCustomer(Customer customer, AccountProduct product, String iban, String name,
                                          Instant openedAt) {
        Account account = new Account();
        account.category = AccountCategory.CUSTOMER;
        account.customer = customer;
        account.product = product;
        account.iban = iban;
        account.name = name;
        account.currency = product.getCurrency();
        account.openedAt = openedAt;
        return account;
    }

}
