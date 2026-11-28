package com.triobank.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.triobank.account.Account;
import com.triobank.account.AccountService;
import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.common.error.FieldErrorCode;
import com.triobank.common.error.FieldErrorDto;
import com.triobank.customer.Customer;
import com.triobank.ledger.LedgerService;
import com.triobank.ledger.PostingCommand;
import com.triobank.ledger.Transaction;
import com.triobank.ledger.transaction.Channel;
import com.triobank.ledger.transaction.TransactionStatus;
import com.triobank.ledger.transaction.TransactionType;
import com.triobank.payment.dto.TransactionDto;
import com.triobank.payment.dto.TransferRequest;
import com.triobank.product.AccountProduct;
import com.triobank.product.OutgoingTransferPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransferServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();
    private static final String KEY = "5b0f3c1e-7a2d-4c8e-9f10-2a3b4c5d6e7f";
    private static final String HASH = "a".repeat(64);
    private static final String SOURCE_IBAN = "PL45999000121000000000000001";
    private static final String OWN_IBAN = "PL72999000121000000000000003";
    private static final String OTHER_IBAN = "PL18999000121000000000000002";
    /** Valid IBAN of another bank (sort code 10901014). */
    private static final String EXTERNAL_IBAN = "PL61109010140000071219812874";

    private final AccountService accountService = mock(AccountService.class);
    private final LedgerService ledgerService = mock(LedgerService.class);
    private final TransferService service = new TransferService(accountService, ledgerService,
            new BankProperties("99900012", "PLN", ZoneId.of("Europe/Warsaw")));

    private Account personal;
    private Account savings;
    private Account othersAccount;

    @BeforeEach
    void customerHasPersonalAndSavingsAccounts() {
        Customer me = customer(CUSTOMER_ID, "Anna", "Nowak");
        personal = account(me, SOURCE_IBAN, OutgoingTransferPolicy.ANY);
        savings = account(me, OWN_IBAN, OutgoingTransferPolicy.OWN_ACCOUNTS_ONLY);
        othersAccount = account(customer(OTHER_CUSTOMER_ID, "Jan", "Kowalski"), OTHER_IBAN,
                OutgoingTransferPolicy.ANY);
        when(accountService.getOfCustomerForOperation(CUSTOMER_ID, personal.getId())).thenReturn(personal);
        when(accountService.getOfCustomerForOperation(CUSTOMER_ID, savings.getId())).thenReturn(savings);
        when(accountService.findForTransfer(SOURCE_IBAN)).thenReturn(Optional.of(personal));
        when(accountService.findForTransfer(OWN_IBAN)).thenReturn(Optional.of(savings));
        when(accountService.findForTransfer(OTHER_IBAN)).thenReturn(Optional.of(othersAccount));
        when(accountService.findForTransfer(EXTERNAL_IBAN)).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------ own transfers

    @Test
    void ownTransferUsesOwnNameAndDefaultTitle() {
        Transaction transaction = posted(personal, savings);
        when(ledgerService.post(any())).thenReturn(transaction);

        TransactionDto result = service.transfer(USER_ID, CUSTOMER_ID,
                request(personal, OWN_IBAN, "Someone Else", null, "150.00", null), KEY, HASH);

        PostingCommand command = postedCommand();
        assertThat(command.type()).isEqualTo(TransactionType.OWN_TRANSFER);
        assertThat(command.channel()).isEqualTo(Channel.WEB);
        assertThat(command.sourceAccountId()).isEqualTo(personal.getId());
        assertThat(command.targetAccountId()).isEqualTo(savings.getId());
        assertThat(command.amount()).isEqualByComparingTo("150.00");
        assertThat(command.currency()).isEqualTo("PLN");
        assertThat(command.title()).isEqualTo("Own transfer");
        assertThat(command.senderName()).isEqualTo("Anna Nowak");
        assertThat(command.recipientName()).as("name in the request is ignored").isEqualTo("Anna Nowak");
        assertThat(command.initiatedByUserId()).isEqualTo(USER_ID);
        assertThat(command.idempotencyKey()).isEqualTo(KEY);
        assertThat(command.requestHash()).isEqualTo(HASH);

        assertThat(result.sender().ownAccount()).isTrue();
        assertThat(result.recipient().ownAccount()).isTrue();
    }

    @Test
    void ownTransferKeepsTheGivenTitle() {
        Transaction transaction = posted(personal, savings);
        when(ledgerService.post(any())).thenReturn(transaction);

        service.transfer(USER_ID, CUSTOMER_ID, request(personal, OWN_IBAN, null, "For holidays", "10.00", "PLN"),
                KEY, HASH);

        assertThat(postedCommand().title()).isEqualTo("For holidays");
    }

    @Test
    void savingsMaySendToOwnAccount() {
        Transaction transaction = posted(savings, personal);
        when(ledgerService.post(any())).thenReturn(transaction);

        service.transfer(USER_ID, CUSTOMER_ID, request(savings, SOURCE_IBAN, null, null, "10.00", null), KEY, HASH);

        assertThat(postedCommand().type()).isEqualTo(TransactionType.OWN_TRANSFER);
    }

    // ------------------------------------------------------- internal transfers

    @Test
    void transferToAnotherCustomerIsInternalWithTheGivenName() {
        Transaction transaction = posted(personal, othersAccount);
        when(ledgerService.post(any())).thenReturn(transaction);

        TransactionDto result = service.transfer(USER_ID, CUSTOMER_ID,
                request(personal, OTHER_IBAN, "Jan Kowalski", null, "150.00", null), KEY, HASH);

        PostingCommand command = postedCommand();
        assertThat(command.type()).isEqualTo(TransactionType.INTERNAL_TRANSFER);
        assertThat(command.title()).isEqualTo("Funds transfer");
        assertThat(command.senderName()).isEqualTo("Anna Nowak");
        assertThat(command.recipientName()).isEqualTo("Jan Kowalski");
        assertThat(result.recipient().ownAccount()).isFalse();
    }

    @Test
    void transferToAnotherCustomerNeedsRecipientName() {
        assertThatThrownBy(() -> service.transfer(USER_ID, CUSTOMER_ID,
                request(personal, OTHER_IBAN, null, null, "150.00", null), KEY, HASH))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getProperties().get("errors")).asInstanceOf(list(FieldErrorDto.class))
                            .extracting(FieldErrorDto::field, FieldErrorDto::code)
                            .containsExactly(tuple("recipientName", FieldErrorCode.REQUIRED));
                });
        verify(ledgerService, never()).post(any());
    }

    @Test
    void savingsCannotSendToAnotherCustomer() {
        assertRejected(request(savings, OTHER_IBAN, "Jan Kowalski", null, "10.00", null),
                ErrorCode.TRANSFER_POLICY_VIOLATION);
    }

    // ------------------------------------------------------ rejected transfers

    @Test
    void foreignSourceAccountIsNotFound() {
        UUID foreign = UUID.randomUUID();
        when(accountService.getOfCustomerForOperation(CUSTOMER_ID, foreign))
                .thenThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Account not found."));

        assertRejected(new TransferRequest(foreign, OWN_IBAN, null, null, new BigDecimal("10.00"), null),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void otherCurrencyIsNotSupported() {
        assertRejected(request(personal, OWN_IBAN, null, null, "10.00", "EUR"), ErrorCode.CURRENCY_NOT_SUPPORTED);
    }

    @Test
    void otherBankIsNotSupported() {
        assertRejected(request(personal, EXTERNAL_IBAN, "Jan Kowalski", null, "10.00", null),
                ErrorCode.EXTERNAL_TRANSFER_NOT_SUPPORTED);
    }

    @Test
    void unknownTrioIbanIsNotFound() {
        String unknown = "PL83999000121000000000000099";
        when(accountService.findForTransfer(unknown)).thenReturn(Optional.empty());

        assertRejected(request(personal, unknown, "Jan Kowalski", null, "10.00", null),
                ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
    }

    @Test
    void sameAccountIsRejected() {
        assertRejected(request(personal, SOURCE_IBAN, null, null, "10.00", null), ErrorCode.SAME_ACCOUNT_TRANSFER);
    }

    // ------------------------------------------------------------- check order

    @Test
    void currencyIsCheckedBeforeTheBank() {
        assertRejected(request(personal, EXTERNAL_IBAN, null, null, "10.00", "EUR"), ErrorCode.CURRENCY_NOT_SUPPORTED);
    }

    @Test
    void recipientNameIsCheckedBeforeThePolicy() {
        assertRejected(request(savings, OTHER_IBAN, null, null, "10.00", null), ErrorCode.VALIDATION_ERROR);
    }

    // ----------------------------------------------------------------- helpers

    private void assertRejected(TransferRequest request, ErrorCode code) {
        assertThatThrownBy(() -> service.transfer(USER_ID, CUSTOMER_ID, request, KEY, HASH))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(code));
        verify(ledgerService, never()).post(any());
    }

    private PostingCommand postedCommand() {
        ArgumentCaptor<PostingCommand> command = ArgumentCaptor.forClass(PostingCommand.class);
        verify(ledgerService).post(command.capture());
        return command.getValue();
    }

    private static TransferRequest request(Account source, String targetIban, String recipientName, String title,
                                           String amount, String currency) {
        return new TransferRequest(source.getId(), targetIban, recipientName, title, new BigDecimal(amount),
                currency);
    }

    private static Customer customer(UUID id, String firstName, String lastName) {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn(id);
        when(customer.getFirstName()).thenReturn(firstName);
        when(customer.getLastName()).thenReturn(lastName);
        return customer;
    }

    private static Account account(Customer owner, String iban, OutgoingTransferPolicy policy) {
        AccountProduct product = mock(AccountProduct.class);
        when(product.getOutgoingTransferPolicy()).thenReturn(policy);
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(UUID.randomUUID());
        when(account.getIban()).thenReturn(iban);
        when(account.getCustomer()).thenReturn(owner);
        when(account.getProduct()).thenReturn(product);
        return account;
    }

    private static Transaction posted(Account source, Account target) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(UUID.randomUUID());
        when(transaction.getReference()).thenReturn("TB202611280000000001");
        when(transaction.getStatus()).thenReturn(TransactionStatus.COMPLETED);
        when(transaction.getChannel()).thenReturn(Channel.WEB);
        when(transaction.getAmount()).thenReturn(new BigDecimal("150.00"));
        when(transaction.getCurrency()).thenReturn("PLN");
        when(transaction.getSourceAccount()).thenReturn(source);
        when(transaction.getTargetAccount()).thenReturn(target);
        when(transaction.getCreatedAt()).thenReturn(Instant.parse("2026-11-28T10:00:00Z"));
        return transaction;
    }

}
