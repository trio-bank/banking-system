package com.triobank.customer;

import com.triobank.auth.User;
import com.triobank.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.EmbeddedColumnNaming;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A bank client and their personal data. Each customer has exactly one user to log in with.
 */
@Getter
@Entity
@Table(name = "customers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 8, updatable = false)
    private String customerNumber;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 11, updatable = false)
    private String pesel;

    @Column(nullable = false, updatable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 16)
    private String phoneNumber;

    @Embedded
    @EmbeddedColumnNaming("address_%s")
    private Address residentialAddress;

    public Customer(User user, String customerNumber, String firstName, String lastName, String pesel,
                    LocalDate dateOfBirth, String phoneNumber, Address residentialAddress) {
        this.user = user;
        this.customerNumber = customerNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.pesel = pesel;
        this.dateOfBirth = dateOfBirth;
        this.phoneNumber = phoneNumber;
        this.residentialAddress = residentialAddress;
    }

}
