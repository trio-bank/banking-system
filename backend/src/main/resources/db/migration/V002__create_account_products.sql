-- Kinds of accounts the bank offers, with the two products we start with.

CREATE TABLE account_products
(
    id                        UUID         NOT NULL,
    code                      VARCHAR(50)  NOT NULL,
    name                      VARCHAR(100) NOT NULL,
    description               VARCHAR(500),
    type                      VARCHAR(20)  NOT NULL,
    currency                  CHAR(3)      NOT NULL,
    interest_rate             NUMERIC(7, 4) NOT NULL,
    outgoing_transfer_policy  VARCHAR(30)  NOT NULL,
    atm_allowed               BOOLEAN      NOT NULL,
    max_accounts_per_customer INTEGER      NOT NULL,
    prerequisite_type         VARCHAR(20),
    active                    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_products PRIMARY KEY (id),
    CONSTRAINT uk_account_products_code UNIQUE (code),
    CONSTRAINT ck_account_products_type CHECK (type IN ('PERSONAL', 'SAVINGS')),
    CONSTRAINT ck_account_products_prerequisite_type CHECK (prerequisite_type IN ('PERSONAL', 'SAVINGS')),
    CONSTRAINT ck_account_products_outgoing_transfer_policy CHECK (outgoing_transfer_policy IN ('ANY', 'OWN_ACCOUNTS_ONLY')),
    CONSTRAINT ck_account_products_interest_rate_non_negative CHECK (interest_rate >= 0),
    CONSTRAINT ck_account_products_max_accounts_positive CHECK (max_accounts_per_customer > 0)
);

INSERT INTO account_products (id, code, name, description, type, currency, interest_rate, outgoing_transfer_policy,
                              atm_allowed, max_accounts_per_customer, prerequisite_type, active, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000101', 'PERSONAL_STANDARD', 'Trio Personal Account',
        'Everyday account for payments, transfers and ATM cash.',
        'PERSONAL', 'PLN', 0.0000, 'ANY', TRUE, 2, NULL, TRUE, NOW(), NOW()),
       ('00000000-0000-4000-8000-000000000102', 'SAVINGS_STANDARD', 'Trio Savings Account',
        'Savings account with interest. Transfers only to your own accounts.',
        'SAVINGS', 'PLN', 4.0000, 'OWN_ACCOUNTS_ONLY', FALSE, 5, 'PERSONAL', TRUE, NOW(), NOW());
