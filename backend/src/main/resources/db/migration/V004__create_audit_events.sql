-- Audit log. Rows are only ever inserted.

CREATE TABLE audit_events
(
    id            UUID         NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    actor_user_id UUID,
    actor_email   VARCHAR(254),
    actor_role    VARCHAR(20),
    action        VARCHAR(50)  NOT NULL,
    target_type   VARCHAR(30),
    target_id     UUID,
    ip_address    VARCHAR(45),
    details       JSONB,
    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT ck_audit_events_actor_role CHECK (actor_role IN ('CUSTOMER', 'ADMIN', 'SYSTEM')),
    CONSTRAINT ck_audit_events_target_type CHECK (target_type IN ('USER', 'CUSTOMER', 'ACCOUNT', 'TRANSACTION', 'JOB')),
    CONSTRAINT ck_audit_events_action CHECK (action IN (
        'USER_REGISTERED', 'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'USER_LOCKED', 'USER_UNLOCKED', 'USER_BLOCKED',
        'USER_UNBLOCKED', 'LOGOUT', 'ACCOUNT_OPENED', 'ACCOUNT_RENAMED', 'ACCOUNT_BLOCKED', 'ACCOUNT_UNBLOCKED',
        'TRANSACTION_POSTED', 'INTEREST_ACCRUAL_RUN', 'INTEREST_CAPITALIZATION_RUN', 'ADMIN_BOOTSTRAPPED'))
);

CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at);
CREATE INDEX idx_audit_events_target_id ON audit_events (target_id);
