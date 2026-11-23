package com.triobank.ledger;

import static com.triobank.BankApi.newKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.triobank.ApiTestBase;
import com.triobank.BankApi.Customer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** docs/09-testing.md §4.2: parallel requests against the real database. */
class WithdrawalConcurrencyIntegrationTest extends ApiTestBase {

    private record Outcome(int status, String code) {
    }

    @FunctionalInterface
    private interface Request {
        MvcResult send(int index) throws Exception;
    }

    @Test
    @Tag("TC-30")
    void TC30_50parallelWithdrawals_ofTen_exactly10Succeed_balanceZero() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");
        int transactionsBefore = countTransactionsOfUser(c.userId());

        List<Outcome> outcomes = runInParallel(50,
                i -> api.withdraw(c.token(), c.personalAccountId(), "10.00", newKey()).andReturn());

        assertThat(outcomes.stream().filter(o -> o.status() == 201)).hasSize(10);
        assertThat(outcomes.stream().filter(o -> o.status() == 422 && "INSUFFICIENT_FUNDS".equals(o.code())))
                .hasSize(40);
        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("0.00");
        assertThat(countTransactionsOfUser(c.userId()) - transactionsBefore).isEqualTo(10);
        // TC-38: invariants are checked once more by LedgerInvariantExtension after the class
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    @Test
    @Tag("TC-31")
    void TC31_10parallelRequestsWithSameKey_createExactlyOneTransaction() throws Exception {
        Customer c = api.newCustomer();
        String key = newKey();

        List<Outcome> outcomes = runInParallel(10,
                i -> api.deposit(c.token(), c.personalAccountId(), "100.00", key).andReturn());

        assertThat(outcomes).as("no server errors").noneMatch(o -> o.status() >= 500);
        assertThat(outcomes.stream().filter(o -> o.status() == 201)).as("created once").hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE initiated_by_user_id = ? AND idempotency_key = ?",
                Integer.class, c.userId(), key)).isEqualTo(1);
        assertThat(api.balance(c.token(), c.personalAccountId())).isEqualByComparingTo("100.00");
    }

    @Test
    @Tag("TC-30")
    void TC30_parallelDepositsAndWithdrawals_keepBalanceNonNegativeAndLedgerBalanced() throws Exception {
        Customer c = api.newCustomerWithBalance("100.00");

        List<Outcome> outcomes = runInParallel(30, i -> i % 2 == 0
                ? api.withdraw(c.token(), c.personalAccountId(), "10.00", newKey()).andReturn()
                : api.deposit(c.token(), c.personalAccountId(), "10.00", newKey()).andReturn());

        assertThat(outcomes).noneMatch(o -> o.status() >= 500);
        assertThat(api.balance(c.token(), c.personalAccountId())).isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO);
        LedgerInvariants.assertLedgerBalanced(jdbc);
    }

    /** All requests wait at a gate and start together. */
    private List<Outcome> runInParallel(int count, Request request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return toOutcome(request.send(index));
                }));
            }
            ready.await();
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Outcome toOutcome(MvcResult result) throws Exception {
        int httpStatus = result.getResponse().getStatus();
        String code = null;
        if (httpStatus >= 400) {
            try {
                code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
            } catch (RuntimeException e) {
                code = "NO_CODE";
            }
        }
        return new Outcome(httpStatus, code);
    }

}
