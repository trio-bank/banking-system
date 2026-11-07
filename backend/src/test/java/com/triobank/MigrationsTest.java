package com.triobank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triobank.account.Account;
import com.triobank.account.AccountCategory;
import com.triobank.account.AccountRepository;
import com.triobank.account.AccountStatus;
import com.triobank.account.SystemAccountCode;
import com.triobank.product.AccountProduct;
import com.triobank.product.AccountProductRepository;
import com.triobank.product.AccountType;
import com.triobank.product.OutgoingTransferPolicy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the migrations on a clean database and checks the seed data and table constraints.
 * The context itself starting proves that entities match the schema ({@code ddl-auto: validate}).
 */
@IntegrationTest
@Transactional
class MigrationsTest {

    private static final String INSERT_ACCOUNT = """
            INSERT INTO accounts (id, iban, category, customer_id, product_id, system_code, name, currency, balance,
                                  opened_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, 'Test', 'PLN', ?, NOW(), NOW(), NOW())
            """;

    @Autowired
    private AccountProductRepository products;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void personalProductIsSeeded() {
        AccountProduct personal = products.findByCode("PERSONAL_STANDARD").orElseThrow();

        assertThat(personal.getName()).isEqualTo("Trio Personal Account");
        assertThat(personal.getType()).isEqualTo(AccountType.PERSONAL);
        assertThat(personal.getCurrency()).isEqualTo("PLN");
        assertThat(personal.getInterestRate()).isEqualByComparingTo("0.0000");
        assertThat(personal.getOutgoingTransferPolicy()).isEqualTo(OutgoingTransferPolicy.ANY);
        assertThat(personal.isAtmAllowed()).isTrue();
        assertThat(personal.getMaxAccountsPerCustomer()).isEqualTo(2);
        assertThat(personal.getPrerequisiteType()).isNull();
        assertThat(personal.isActive()).isTrue();
    }

    @Test
    void savingsProductIsSeeded() {
        AccountProduct savings = products.findByCode("SAVINGS_STANDARD").orElseThrow();

        assertThat(savings.getName()).isEqualTo("Trio Savings Account");
        assertThat(savings.getType()).isEqualTo(AccountType.SAVINGS);
        assertThat(savings.getInterestRate()).isEqualByComparingTo("4.0000");
        assertThat(savings.getOutgoingTransferPolicy()).isEqualTo(OutgoingTransferPolicy.OWN_ACCOUNTS_ONLY);
        assertThat(savings.isAtmAllowed()).isFalse();
        assertThat(savings.getMaxAccountsPerCustomer()).isEqualTo(5);
        assertThat(savings.getPrerequisiteType()).isEqualTo(AccountType.PERSONAL);
    }

    @Test
    void onlyTwoProductsAreSeeded() {
        assertThat(products.findByActiveTrueOrderByCode())
                .extracting(AccountProduct::getCode)
                .containsExactly("PERSONAL_STANDARD", "SAVINGS_STANDARD");
    }

    @Test
    void fourSystemAccountsAreSeededWithValidIbans() {
        assertThat(ibanOf(SystemAccountCode.BANK_CASH)).isEqualTo("PL25999000129000000000000001");
        assertThat(ibanOf(SystemAccountCode.BANK_INTEREST_EXPENSE)).isEqualTo("PL95999000129000000000000002");
        assertThat(ibanOf(SystemAccountCode.BANK_TAX_LIABILITY)).isEqualTo("PL68999000129000000000000003");
        assertThat(ibanOf(SystemAccountCode.BANK_FEE_INCOME)).isEqualTo("PL41999000129000000000000004");

        for (SystemAccountCode code : SystemAccountCode.values()) {
            assertThat(isValidIban(ibanOf(code))).as(code.name()).isTrue();
        }
    }

    @Test
    void systemAccountsStartActiveWithZeroBalance() {
        Account cash = accounts.findBySystemCode(SystemAccountCode.BANK_CASH).orElseThrow();

        assertThat(cash.getCategory()).isEqualTo(AccountCategory.SYSTEM);
        assertThat(cash.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(cash.getBalance()).isEqualByComparingTo("0.00");
        assertThat(cash.getCustomer()).isNull();
        assertThat(cash.getProduct()).isNull();
    }

    @Test
    void customerAccountWithNegativeBalanceIsRejected() {
        UUID customerId = insertCustomer();
        UUID productId = products.findByCode("PERSONAL_STANDARD").orElseThrow().getId();

        assertThatThrownBy(() -> jdbc.update(INSERT_ACCOUNT, UUID.randomUUID(), "PL45999000121000000000000001",
                "CUSTOMER", customerId, productId, null, new BigDecimal("-0.01")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_balance_non_negative");
    }

    @Test
    void systemAccountMayHaveNegativeBalance() {
        jdbc.update("UPDATE accounts SET balance = -100.00 WHERE system_code = 'BANK_CASH'");

        BigDecimal balance = jdbc.queryForObject("SELECT balance FROM accounts WHERE system_code = 'BANK_CASH'",
                BigDecimal.class);
        assertThat(balance).isEqualByComparingTo("-100.00");
    }

    @Test
    void customerAccountWithoutCustomerIsRejected() {
        UUID productId = products.findByCode("PERSONAL_STANDARD").orElseThrow().getId();

        assertThatThrownBy(() -> jdbc.update(INSERT_ACCOUNT, UUID.randomUUID(), "PL45999000121000000000000001",
                "CUSTOMER", null, productId, null, BigDecimal.ZERO))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_category_consistency");
    }

    @Test
    void systemAccountWithCustomerIsRejected() {
        UUID customerId = insertCustomer();

        assertThatThrownBy(() -> jdbc.update(INSERT_ACCOUNT, UUID.randomUUID(), "PL41999000129000000000000005",
                "SYSTEM", customerId, null, "BANK_FEE_INCOME", BigDecimal.ZERO))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_category_consistency");
    }

    @Test
    void unknownEnumValueIsRejected() {
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET status = 'FROZEN' WHERE system_code = 'BANK_CASH'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_status");
    }

    @Test
    void sequencesStartAtDocumentedValues() {
        assertThat(jdbc.queryForObject("SELECT start_value FROM pg_sequences WHERE sequencename = 'customer_number_seq'",
                Long.class)).isEqualTo(10_000_001L);
        assertThat(jdbc.queryForObject("SELECT start_value FROM pg_sequences WHERE sequencename = 'account_number_seq'",
                Long.class)).isEqualTo(1_000_000_000_000_001L);
    }

    private String ibanOf(SystemAccountCode code) {
        return accounts.findBySystemCode(code).orElseThrow().getIban();
    }

    /** ISO 13616: move the first 4 characters to the end, letters to numbers, mod 97 must be 1. */
    private static boolean isValidIban(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.getNumericValue(c));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    private UUID insertCustomer() {
        UUID userId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, role, created_at, updated_at)
                VALUES (?, ?, 'hash', 'CUSTOMER', NOW(), NOW())
                """, userId, userId + "@example.com");
        jdbc.update("""
                INSERT INTO customers (id, user_id, customer_number, first_name, last_name, pesel, date_of_birth,
                                       phone_number, address_street, address_building_number, address_postal_code,
                                       address_city, address_country_code, created_at, updated_at)
                VALUES (?, ?, '19999999', 'Anna', 'Nowak', '90051412343', DATE '1990-05-14', '+48501234567',
                        'Marszalkowska', '1', '00-001', 'Warszawa', 'PL', NOW(), NOW())
                """, customerId, userId);
        return customerId;
    }

}
