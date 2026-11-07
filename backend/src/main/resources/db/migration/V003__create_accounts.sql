-- Customer accounts and the bank's own system accounts.

-- Numbers for customer accounts. System accounts have fixed numbers starting with 9 and do not use this sequence.
CREATE SEQUENCE account_number_seq START WITH 1000000000000001 MAXVALUE 8999999999999999;

CREATE TABLE accounts
(
    id                UUID          NOT NULL,
    iban              VARCHAR(34)   NOT NULL,
    category          VARCHAR(20)   NOT NULL,
    customer_id       UUID,
    product_id        UUID,
    system_code       VARCHAR(50),
    name              VARCHAR(50)   NOT NULL,
    currency          CHAR(3)       NOT NULL,
    balance           NUMERIC(19, 2) NOT NULL DEFAULT 0,
    accrued_interest  NUMERIC(19, 6) NOT NULL DEFAULT 0,
    last_accrual_date DATE,
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    status_reason     VARCHAR(255),
    opened_at         TIMESTAMPTZ   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_accounts_product FOREIGN KEY (product_id) REFERENCES account_products (id),
    CONSTRAINT uk_accounts_iban UNIQUE (iban),
    CONSTRAINT uk_accounts_system_code UNIQUE (system_code),
    CONSTRAINT ck_accounts_category CHECK (category IN ('CUSTOMER', 'SYSTEM')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
    CONSTRAINT ck_accounts_system_code CHECK (system_code IN ('BANK_CASH', 'BANK_INTEREST_EXPENSE',
                                                              'BANK_TAX_LIABILITY', 'BANK_FEE_INCOME')),
    CONSTRAINT ck_accounts_category_consistency CHECK (
        (category = 'CUSTOMER' AND customer_id IS NOT NULL AND product_id IS NOT NULL AND system_code IS NULL)
            OR (category = 'SYSTEM' AND customer_id IS NULL AND product_id IS NULL AND system_code IS NOT NULL)),
    CONSTRAINT ck_accounts_balance_non_negative CHECK (category = 'SYSTEM' OR balance >= 0),
    CONSTRAINT ck_accounts_accrued_interest_non_negative CHECK (accrued_interest >= 0)
);

CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);
CREATE INDEX idx_accounts_status ON accounts (status);

-- IBANs are precomputed for sort code 99900012. MigrationsTest checks that they are valid.
INSERT INTO accounts (id, iban, category, system_code, name, currency, opened_at, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000201', 'PL25999000129000000000000001', 'SYSTEM', 'BANK_CASH',
        'BANK_CASH', 'PLN', NOW(), NOW(), NOW()),
       ('00000000-0000-4000-8000-000000000202', 'PL95999000129000000000000002', 'SYSTEM', 'BANK_INTEREST_EXPENSE',
        'BANK_INTEREST_EXPENSE', 'PLN', NOW(), NOW(), NOW()),
       ('00000000-0000-4000-8000-000000000203', 'PL68999000129000000000000003', 'SYSTEM', 'BANK_TAX_LIABILITY',
        'BANK_TAX_LIABILITY', 'PLN', NOW(), NOW(), NOW()),
       ('00000000-0000-4000-8000-000000000204', 'PL41999000129000000000000004', 'SYSTEM', 'BANK_FEE_INCOME',
        'BANK_FEE_INCOME', 'PLN', NOW(), NOW(), NOW());
