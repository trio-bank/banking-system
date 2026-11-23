package com.triobank.ledger;

import static com.triobank.BankApi.SAVINGS;
import static com.triobank.BankApi.newKey;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.triobank.ApiTestBase;
import com.triobank.BankApi.Customer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** TC-38: ledger invariants hold after real operations, and the check really fails on a broken ledger. */
class LedgerInvariantTest extends ApiTestBase {

    @Test
    @Tag("TC-38")
    void TC38_invariantsHold_afterDepositsAndWithdrawals() throws Exception {
        Customer c = api.newCustomerWithBalance("1000.00");
        api.deposit(c.token(), c.personalAccountId(), "250.00", newKey()).andExpect(status().isCreated());
        api.withdraw(c.token(), c.personalAccountId(), "400.00", newKey()).andExpect(status().isCreated());
        api.withdraw(c.token(), c.personalAccountId(), "99999.00", newKey()).andExpect(status().isUnprocessableEntity());
        api.openAccountId(c.token(), SAVINGS, null);

        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    @Test
    @Tag("TC-38")
    void TC38_check_failsWhenBalanceDiffersFromEntries() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        jdbc.update("UPDATE accounts SET balance = balance + 1 WHERE id = ?", c.personalAccountId());
        try {
            assertThatThrownBy(() -> LedgerInvariants.assertLedgerBalanced(jdbc)).isInstanceOf(AssertionError.class);
        } finally {
            jdbc.update("UPDATE accounts SET balance = balance - 1 WHERE id = ?", c.personalAccountId());
        }
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

}
