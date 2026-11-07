package com.triobank.audit;

/**
 * What happened. Adding a value needs a migration that extends ck_audit_events_action.
 */
public enum AuditAction {

    USER_REGISTERED,
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    USER_LOCKED,
    USER_UNLOCKED,
    USER_BLOCKED,
    USER_UNBLOCKED,
    LOGOUT,
    ACCOUNT_OPENED,
    ACCOUNT_RENAMED,
    ACCOUNT_BLOCKED,
    ACCOUNT_UNBLOCKED,
    TRANSACTION_POSTED,
    INTEREST_ACCRUAL_RUN,
    INTEREST_CAPITALIZATION_RUN,
    ADMIN_BOOTSTRAPPED

}
