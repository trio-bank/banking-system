package com.triobank.product;

import com.triobank.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A kind of account the bank offers. Products come from migrations; nobody creates them through the API.
 */
@Getter
@Entity
@Table(name = "account_products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountProduct extends BaseEntity {

    @Column(nullable = false, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutgoingTransferPolicy outgoingTransferPolicy;

    @Column(nullable = false)
    private boolean atmAllowed;

    @Column(nullable = false)
    private int maxAccountsPerCustomer;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AccountType prerequisiteType;

    @Column(nullable = false)
    private boolean active;

}
