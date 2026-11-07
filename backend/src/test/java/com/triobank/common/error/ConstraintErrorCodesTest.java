package com.triobank.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConstraintErrorCodesTest {

    @Test
    void uniqueEmailAndPeselMapToConflictCodes() {
        assertThat(ConstraintErrorCodes.forConstraint("uk_users_email")).contains(ErrorCode.EMAIL_ALREADY_REGISTERED);
        assertThat(ConstraintErrorCodes.forConstraint("uk_customers_pesel")).contains(ErrorCode.PESEL_ALREADY_REGISTERED);
    }

    @Test
    void lookupIgnoresCase() {
        assertThat(ConstraintErrorCodes.forConstraint("UK_USERS_EMAIL")).contains(ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void unknownOrMissingConstraintHasNoCode() {
        assertThat(ConstraintErrorCodes.forConstraint("ck_accounts_balance_non_negative")).isEmpty();
        assertThat(ConstraintErrorCodes.forConstraint(null)).isEmpty();
    }

}
