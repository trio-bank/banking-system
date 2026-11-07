package com.triobank.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triobank.IntegrationTest;
import com.triobank.account.Account;
import com.triobank.account.AccountCategory;
import com.triobank.account.AccountRepository;
import com.triobank.account.AccountStatus;
import com.triobank.auth.Role;
import com.triobank.auth.User;
import com.triobank.auth.UserRepository;
import com.triobank.auth.UserStatus;
import com.triobank.product.AccountProduct;
import com.triobank.product.AccountProductRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users, customers and customer accounts round-trip through JPA on the real schema.
 */
@IntegrationTest
@Transactional
class CustomerPersistenceTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private AccountProductRepository products;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private EntityManager entityManager;

    @Test
    void customerWithAddressAndAccountIsSavedAndLoaded() {
        User user = users.save(new User("Anna.Nowak@Example.com", "$2a$10$hash", Role.CUSTOMER));
        Customer customer = customers.save(new Customer(user, customers.nextCustomerNumber(), "Anna", "Nowak",
                "90051412343", LocalDate.of(1990, 5, 14), "+48501234567",
                new Address("Marszałkowska", "12A", null, "00-001", "Warszawa", "PL")));
        AccountProduct personal = products.findByCode("PERSONAL_STANDARD").orElseThrow();
        Account account = accounts.save(Account.openForCustomer(customer, personal,
                "PL45999000121000000000000001", personal.getName(), Instant.parse("2026-11-07T10:00:00Z")));
        entityManager.flush();
        entityManager.clear();

        User loadedUser = users.findByEmail("anna.nowak@example.com").orElseThrow();
        assertThat(loadedUser.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(loadedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loadedUser.getFailedLoginAttempts()).isZero();
        assertThat(loadedUser.getCreatedAt()).isNotNull();
        assertThat(loadedUser.getUpdatedAt()).isNotNull();

        Customer loaded = customers.findByUserId(user.getId()).orElseThrow();
        assertThat(loaded.getCustomerNumber()).matches("\\d{8}");
        assertThat(loaded.getPesel()).isEqualTo("90051412343");
        assertThat(loaded.getResidentialAddress().getStreet()).isEqualTo("Marszałkowska");
        assertThat(loaded.getResidentialAddress().getBuildingNumber()).isEqualTo("12A");
        assertThat(loaded.getResidentialAddress().getApartmentNumber()).isNull();
        assertThat(loaded.getResidentialAddress().getCountryCode()).isEqualTo("PL");

        Account loadedAccount = accounts.findByIban("PL45999000121000000000000001").orElseThrow();
        assertThat(loadedAccount.getId()).isEqualTo(account.getId());
        assertThat(loadedAccount.getCategory()).isEqualTo(AccountCategory.CUSTOMER);
        assertThat(loadedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(loadedAccount.getCurrency()).isEqualTo("PLN");
        assertThat(loadedAccount.getBalance()).isEqualByComparingTo("0.00");
        assertThat(loadedAccount.getAccruedInterest()).isEqualByComparingTo("0");
        assertThat(loadedAccount.getCustomer().getId()).isEqualTo(customer.getId());
        assertThat(loadedAccount.getProduct().getCode()).isEqualTo("PERSONAL_STANDARD");
    }

    @Test
    void duplicateEmailViolatesNamedConstraint() {
        users.saveAndFlush(new User("jan.kowalski@example.com", "hash", Role.CUSTOMER));

        assertThatThrownBy(() -> users.saveAndFlush(new User("JAN.KOWALSKI@example.com", "hash", Role.CUSTOMER)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class)
                .satisfies(ex -> assertThat(constraintName(ex)).isEqualTo("uk_users_email"));
    }

    @Test
    void sequencesGiveDocumentedNumberFormats() {
        assertThat(customers.nextCustomerNumber()).matches("\\d{8}");
        assertThat(accounts.nextAccountNumber()).matches("1\\d{15}");
    }

    private static String constraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

}
