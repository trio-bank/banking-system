package com.triobank.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.triobank.account.Account;
import com.triobank.account.AccountCategory;
import com.triobank.account.AccountService;
import com.triobank.account.AccountStatus;
import com.triobank.account.PostingAccounts;
import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.ledger.transaction.Channel;
import com.triobank.ledger.transaction.EntryDirection;
import com.triobank.ledger.transaction.TransactionStatus;
import com.triobank.ledger.transaction.TransactionType;
import com.triobank.product.AccountProduct;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LedgerServiceTest {

    // 23:30 UTC is already the next day in Warsaw
    private static final Instant NOW = Instant.parse("2026-11-18T23:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private final AccountService accountService = mock(AccountService.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final LedgerEntryRepository entries = mock(LedgerEntryRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    private final LedgerService ledger = new LedgerService(accountService, transactions, entries, auditService,
            new BankProperties("99900012", "PLN", ZoneId.of("Europe/Warsaw")), Clock.fixed(NOW, ZoneOffset.UTC));

    private Account customer;
    private Account cash;

    @BeforeEach
    void setUp() {
        customer = customerAccount("100.00");
        cash = systemAccount();
        when(transactions.save(any(Transaction.class))).thenAnswer(call -> call.getArgument(0));
        when(transactions.nextReferenceNumber()).thenReturn(123L);
    }

    @Test
    void withdrawalMovesMoneyAndWritesTransactionWithTwoEntries() {
        givenAccounts(customer, cash);

        Transaction transaction = ledger.post(command(customer, cash, "30.00"));

        assertThat(customer.getBalance()).isEqualByComparingTo("70.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("30.00");
        assertThat(transaction.getReference()).isEqualTo("TB202611190000000123");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getType()).isEqualTo(TransactionType.CASH_WITHDRAWAL);
        assertThat(transaction.getChannel()).isEqualTo(Channel.ATM);
        assertThat(transaction.getAmount()).isEqualTo(new BigDecimal("30.00"));
        assertThat(transaction.getSourceAccount()).isSameAs(customer);
        assertThat(transaction.getTargetAccount()).isSameAs(cash);
        assertThat(transaction.getInitiatedByUserId()).isEqualTo(USER_ID);
        assertThat(transaction.getCreatedAt()).isEqualTo(NOW);
        assertThat(transaction.getBookedAt()).isEqualTo(NOW);

        assertThat(savedEntries())
                .extracting(LedgerEntry::getDirection, LedgerEntry::getAccount, LedgerEntry::getAmount,
                        LedgerEntry::getBalanceAfter, LedgerEntry::getTransaction)
                .containsExactly(
                        tuple(EntryDirection.DEBIT, customer, new BigDecimal("30.00"), new BigDecimal("70.00"),
                                transaction),
                        tuple(EntryDirection.CREDIT, cash, new BigDecimal("30.00"), new BigDecimal("30.00"),
                                transaction));
    }

    @Test
    void wholeBalanceCanBeTaken() {
        givenAccounts(customer, cash);

        ledger.post(command(customer, cash, "100"));

        assertThat(customer.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void amountIsStoredWithTwoDecimals() {
        givenAccounts(customer, cash);

        Transaction transaction = ledger.post(command(customer, cash, "5"));

        assertThat(transaction.getAmount()).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void systemAccountMayGoBelowZero() {
        givenAccounts(cash, customer);

        ledger.post(command(cash, customer, "250.00"));

        assertThat(cash.getBalance()).isEqualByComparingTo("-250.00");
        assertThat(customer.getBalance()).isEqualByComparingTo("350.00");
        assertThat(savedEntries()).extracting(LedgerEntry::getBalanceAfter)
                .containsExactly(new BigDecimal("-250.00"), new BigDecimal("350.00"));
    }

    @Test
    void moreThanBalanceIsRejected() {
        givenAccounts(customer, cash);

        assertError(() -> ledger.post(command(customer, cash, "100.01")), ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(customer.getBalance()).isEqualByComparingTo("100.00");
        assertNothingWritten();
    }

    @Test
    void inactiveSourceIsRejected() {
        block(customer);
        givenAccounts(customer, cash);

        assertError(() -> ledger.post(command(customer, cash, "10.00")), ErrorCode.SOURCE_ACCOUNT_UNAVAILABLE);
        assertNothingWritten();
    }

    @Test
    void inactiveTargetIsRejected() {
        block(customer);
        givenAccounts(cash, customer);

        assertError(() -> ledger.post(command(cash, customer, "10.00")), ErrorCode.RECIPIENT_ACCOUNT_UNAVAILABLE);
        assertNothingWritten();
    }

    @Test
    void sameAccountIsRejectedBeforeLoadingAccounts() {
        assertError(() -> ledger.post(command(customer, customer, "10.00")), ErrorCode.SAME_ACCOUNT_TRANSFER);
        verifyNoInteractions(accountService);
        assertNothingWritten();
    }

    @Test
    void currencyOtherThanAccountsIsRejected() {
        givenAccounts(customer, cash);
        PostingCommand euro = PostingCommand.builder()
                .type(TransactionType.CASH_WITHDRAWAL).channel(Channel.ATM)
                .sourceAccountId(customer.getId()).targetAccountId(cash.getId())
                .amount(new BigDecimal("10.00")).currency("EUR")
                .title("ATM cash withdrawal").senderName("Anna Nowak").recipientName("Trio Bank")
                .build();

        assertError(() -> ledger.post(euro), ErrorCode.CURRENCY_NOT_SUPPORTED);
        assertNothingWritten();
    }

    @Test
    void postingIsAudited() {
        givenAccounts(customer, cash);

        Transaction transaction = ledger.post(command(customer, cash, "30.00"));

        ArgumentCaptor<AuditEventCommand> event = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().action()).isEqualTo(AuditAction.TRANSACTION_POSTED);
        assertThat(event.getValue().actorUserId()).isEqualTo(USER_ID);
        assertThat(event.getValue().actorRole()).isEqualTo("CUSTOMER");
        assertThat(event.getValue().targetType()).isEqualTo("TRANSACTION");
        assertThat(event.getValue().targetId()).isEqualTo(transaction.getId());
        assertThat(event.getValue().details())
                .containsEntry("reference", "TB202611190000000123")
                .containsEntry("amount", "30.00")
                .containsEntry("type", "CASH_WITHDRAWAL");
    }

    @Test
    void postingWithoutUserIsAuditedAsSystem() {
        givenAccounts(cash, customer);
        PostingCommand job = PostingCommand.builder()
                .type(TransactionType.INTEREST).channel(Channel.SYSTEM)
                .sourceAccountId(cash.getId()).targetAccountId(customer.getId())
                .amount(new BigDecimal("1.00")).currency("PLN")
                .title("Interest capitalization 2026-10").senderName("Trio Bank").recipientName("Anna Nowak")
                .build();

        ledger.post(job);

        ArgumentCaptor<AuditEventCommand> event = ArgumentCaptor.forClass(AuditEventCommand.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().actorRole()).isEqualTo("SYSTEM");
        assertThat(event.getValue().actorUserId()).isNull();
    }

    private PostingCommand command(Account source, Account target, String amount) {
        return PostingCommand.builder()
                .type(TransactionType.CASH_WITHDRAWAL)
                .channel(Channel.ATM)
                .sourceAccountId(source.getId())
                .targetAccountId(target.getId())
                .amount(new BigDecimal(amount))
                .currency("PLN")
                .title("ATM cash withdrawal")
                .senderName("Anna Nowak")
                .recipientName("Trio Bank")
                .initiatedByUserId(USER_ID)
                .idempotencyKey(UUID.randomUUID().toString())
                .requestHash("a".repeat(64))
                .build();
    }

    private void givenAccounts(Account source, Account target) {
        when(accountService.getForPosting(source.getId(), target.getId()))
                .thenReturn(new PostingAccounts(source, target));
    }

    @SuppressWarnings("unchecked")
    private List<LedgerEntry> savedEntries() {
        ArgumentCaptor<List<LedgerEntry>> saved = ArgumentCaptor.forClass(List.class);
        verify(entries).saveAll(saved.capture());
        return saved.getValue();
    }

    private void assertNothingWritten() {
        verify(transactions, never()).save(any());
        verify(entries, never()).saveAll(any());
        verifyNoInteractions(auditService);
    }

    private static void assertError(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
    }

    private static Account customerAccount(String balance) {
        AccountProduct product = mock(AccountProduct.class);
        when(product.getCurrency()).thenReturn("PLN");
        Account account = Account.openForCustomer(null, product, "PL45999000121000000000000001",
                "Trio Personal Account", NOW);
        account.credit(new BigDecimal(balance));
        return account;
    }

    private static Account systemAccount() {
        Account account = customerAccount("0.00");
        ReflectionTestUtils.setField(account, "category", AccountCategory.SYSTEM);
        ReflectionTestUtils.setField(account, "iban", "PL25999000129000000000000001");
        return account;
    }

    private static void block(Account account) {
        ReflectionTestUtils.setField(account, "status", AccountStatus.BLOCKED);
    }

}
