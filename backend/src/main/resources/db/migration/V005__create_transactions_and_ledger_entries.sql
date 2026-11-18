-- Money movements. A transaction is one business operation; it always has exactly two ledger entries,
-- a debit on the source account and a credit on the target account. Rows are only ever inserted.

-- Running number in the transaction reference: TB + yyyyMMdd + 10 digits.
CREATE SEQUENCE transaction_reference_seq START WITH 1 MAXVALUE 9999999999;

CREATE TABLE transactions
(
    id                   UUID           NOT NULL,
    reference            VARCHAR(24)    NOT NULL,
    type                 VARCHAR(30)    NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    channel              VARCHAR(20)    NOT NULL,
    amount               NUMERIC(19, 2) NOT NULL,
    currency             CHAR(3)        NOT NULL,
    source_account_id    UUID           NOT NULL,
    target_account_id    UUID           NOT NULL,
    sender_name          VARCHAR(140)   NOT NULL,
    recipient_name       VARCHAR(140)   NOT NULL,
    title                VARCHAR(140)   NOT NULL,
    initiated_by_user_id UUID,
    idempotency_key      VARCHAR(64),
    request_hash         CHAR(64),
    created_at           TIMESTAMPTZ    NOT NULL,
    booked_at            TIMESTAMPTZ,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_source_account FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_target_account FOREIGN KEY (target_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_initiated_by_user FOREIGN KEY (initiated_by_user_id) REFERENCES users (id),
    CONSTRAINT uk_transactions_reference UNIQUE (reference),
    CONSTRAINT uk_transactions_idempotency_key UNIQUE (initiated_by_user_id, idempotency_key),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_different_accounts CHECK (source_account_id <> target_account_id),
    -- a key without its request hash could not tell a replay from a reuse
    CONSTRAINT ck_transactions_idempotency_hash CHECK ((idempotency_key IS NULL) = (request_hash IS NULL)),
    CONSTRAINT ck_transactions_type CHECK (type IN (
        'OWN_TRANSFER', 'INTERNAL_TRANSFER', 'CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'INTEREST', 'TAX',
        'FEE', 'EXTERNAL_TRANSFER_OUT', 'EXTERNAL_TRANSFER_IN', 'PHONE_TRANSFER', 'BLIK_PAYMENT', 'BLIK_WITHDRAWAL',
        'CARD_PAYMENT', 'FX_EXCHANGE')),
    CONSTRAINT ck_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_transactions_channel CHECK (channel IN ('WEB', 'ATM', 'BRANCH', 'SYSTEM', 'MOBILE', 'API'))
);

CREATE INDEX idx_transactions_created_at ON transactions (created_at);
CREATE INDEX idx_transactions_type ON transactions (type);
CREATE INDEX idx_transactions_source_account_id ON transactions (source_account_id);
CREATE INDEX idx_transactions_target_account_id ON transactions (target_account_id);

CREATE TABLE ledger_entries
(
    id             UUID           NOT NULL,
    transaction_id UUID           NOT NULL,
    account_id     UUID           NOT NULL,
    direction      VARCHAR(10)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    balance_after  NUMERIC(19, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT uk_ledger_entries_transaction_direction UNIQUE (transaction_id, direction),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT'))
);

-- Account history: newest entries of one account first.
CREATE INDEX idx_ledger_entries_account_created_at ON ledger_entries (account_id, created_at DESC);
