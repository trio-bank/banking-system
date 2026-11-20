package com.triobank.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triobank.account.Account;
import com.triobank.account.AccountService;
import com.triobank.account.SystemAccountCode;
import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.customer.Customer;
import com.triobank.ledger.Channel;
import com.triobank.ledger.LedgerService;
import com.triobank.ledger.PostingCommand;
import com.triobank.ledger.Transaction;
import com.triobank.ledger.TransactionStatus;
import com.triobank.ledger.TransactionType;
import com.triobank.payment.dto.CashOperationRequest;
import com.triobank.payment.dto.TransactionDto;
import com.triobank.product.AccountProduct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AtmServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID BANK_CASH_ID = UUID.randomUUID();
    private static final String KEY = "5b0f3c1e-7a2d-4c8e-9f10-2a3b4c5d6e7f";
    private static final String HASH = "a".repeat(64);

    private final AccountService accountService = mock(AccountService.class);
    private final LedgerService ledgerService = mock(LedgerService.class);
    private final AtmService service = new AtmService(accountService, ledgerService,
            new BankProperties("99900012", "PLN", ZoneId.of("Europe/Warsaw")));

    private Account account;
    private AccountProduct product;

    @BeforeEach
    void customerHasPersonalAccount() {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(CUSTOMER_ID);
        when(customer.getFirstName()).thenReturn("Anna");
        when(customer.getLastName()).thenReturn("Nowak");
        product = mock(AccountProduct.class);
        when(product.isAtmAllowed()).thenReturn(true);
        account = mock(Account.class);
        when(account.getId()).thenReturn(ACCOUNT_ID);
        when(account.getIban()).thenReturn("PL45999000121000000000000001");
        when(account.getCustomer()).thenReturn(customer);
        when(account.getProduct()).thenReturn(product);
        when(accountService.getOfCustomerForOperation(CUSTOMER_ID, ACCOUNT_ID)).thenReturn(account);
        when(accountService.getSystemAccountId(SystemAccountCode.BANK_CASH)).thenReturn(BANK_CASH_ID);
    }

    @Test
    void depositPostsFromBankCashToTheAccount() {
        Transaction posted = postedTransaction();
        when(ledgerService.post(any())).thenReturn(posted);

        TransactionDto result = service.deposit(USER_ID, CUSTOMER_ID, request("200.00", null), KEY, HASH);

        ArgumentCaptor<PostingCommand> command = ArgumentCaptor.forClass(PostingCommand.class);
        verify(ledgerService).post(command.capture());
        assertThat(command.getValue().type()).isEqualTo(TransactionType.CASH_DEPOSIT);
        assertThat(command.getValue().channel()).isEqualTo(Channel.ATM);
        assertThat(command.getValue().sourceAccountId()).isEqualTo(BANK_CASH_ID);
        assertThat(command.getValue().targetAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(command.getValue().amount()).isEqualByComparingTo("200.00");
        assertThat(command.getValue().currency()).isEqualTo("PLN");
        assertThat(command.getValue().title()).isEqualTo("ATM cash deposit");
        assertThat(command.getValue().senderName()).isEqualTo("Trio Bank");
        assertThat(command.getValue().recipientName()).isEqualTo("Anna Nowak");
        assertThat(command.getValue().initiatedByUserId()).isEqualTo(USER_ID);
        assertThat(command.getValue().idempotencyKey()).isEqualTo(KEY);
        assertThat(command.getValue().requestHash()).isEqualTo(HASH);

        assertThat(result.id()).isEqualTo(posted.getId());
        assertThat(result.sender().iban()).isNull();
        assertThat(result.sender().ownAccount()).isFalse();
        assertThat(result.recipient().iban()).isEqualTo("PL45999000121000000000000001");
        assertThat(result.recipient().ownAccount()).isTrue();
    }

    @Test
    void explicitPlnIsAccepted() {
        Transaction posted = postedTransaction();
        when(ledgerService.post(any())).thenReturn(posted);

        service.deposit(USER_ID, CUSTOMER_ID, request("10.00", "PLN"), KEY, HASH);

        verify(ledgerService).post(any());
    }

    @Test
    void missingCurrencyMeansBankCurrency() {
        assertThat(service.resolveCurrency(null)).isEqualTo("PLN");
        assertThat(service.resolveCurrency("EUR")).isEqualTo("EUR");
    }

    @Test
    void foreignAccountIsNotFound() {
        UUID foreign = UUID.randomUUID();
        when(accountService.getOfCustomerForOperation(CUSTOMER_ID, foreign))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found."));

        assertCode(() -> service.deposit(USER_ID, CUSTOMER_ID,
                new CashOperationRequest(foreign, new BigDecimal("200.00"), "EUR"), KEY, HASH),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void currencyIsCheckedBeforeAmount() {
        assertCode(() -> service.deposit(USER_ID, CUSTOMER_ID, request("15.00", "EUR"), KEY, HASH),
                ErrorCode.CURRENCY_NOT_SUPPORTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "9.99", "15.00", "100.50", "20000.01", "20010.00", "25000.00"})
    void amountOutsideAtmLimitsIsRejected(String amount) {
        assertCode(() -> service.deposit(USER_ID, CUSTOMER_ID, request(amount, null), KEY, HASH),
                ErrorCode.CASH_AMOUNT_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10", "10.00", "990.00", "20000.00"})
    void amountsWithinAtmLimitsArePosted(String amount) {
        Transaction posted = postedTransaction();
        when(ledgerService.post(any())).thenReturn(posted);

        service.deposit(USER_ID, CUSTOMER_ID, request(amount, null), KEY, HASH);

        verify(ledgerService).post(any());
    }

    @Test
    void amountIsCheckedBeforeAccountType() {
        when(product.isAtmAllowed()).thenReturn(false);

        assertCode(() -> service.deposit(USER_ID, CUSTOMER_ID, request("15.00", null), KEY, HASH),
                ErrorCode.CASH_AMOUNT_INVALID);
    }

    @Test
    void accountWithoutAtmIsRejected() {
        when(product.isAtmAllowed()).thenReturn(false);

        assertCode(() -> service.deposit(USER_ID, CUSTOMER_ID, request("200.00", null), KEY, HASH),
                ErrorCode.OPERATION_NOT_ALLOWED_FOR_ACCOUNT);
    }

    private void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
        verify(ledgerService, never()).post(any());
    }

    private static CashOperationRequest request(String amount, String currency) {
        return new CashOperationRequest(ACCOUNT_ID, new BigDecimal(amount), currency);
    }

    private Transaction postedTransaction() {
        Account bankCash = mock(Account.class);
        when(bankCash.isSystem()).thenReturn(true);
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(UUID.randomUUID());
        when(transaction.getReference()).thenReturn("TB202611200000000001");
        when(transaction.getType()).thenReturn(TransactionType.CASH_DEPOSIT);
        when(transaction.getStatus()).thenReturn(TransactionStatus.COMPLETED);
        when(transaction.getChannel()).thenReturn(Channel.ATM);
        when(transaction.getAmount()).thenReturn(new BigDecimal("200.00"));
        when(transaction.getCurrency()).thenReturn("PLN");
        when(transaction.getTitle()).thenReturn("ATM cash deposit");
        when(transaction.getSenderName()).thenReturn("Trio Bank");
        when(transaction.getRecipientName()).thenReturn("Anna Nowak");
        when(transaction.getSourceAccount()).thenReturn(bankCash);
        when(transaction.getTargetAccount()).thenReturn(account);
        when(transaction.getCreatedAt()).thenReturn(Instant.parse("2026-11-20T10:00:00Z"));
        when(transaction.getBookedAt()).thenReturn(Instant.parse("2026-11-20T10:00:00Z"));
        return transaction;
    }

}
