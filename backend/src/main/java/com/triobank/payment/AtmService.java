package com.triobank.payment;

import com.triobank.account.Account;
import com.triobank.account.AccountService;
import com.triobank.account.SystemAccountCode;
import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.ledger.LedgerService;
import com.triobank.ledger.PostingCommand;
import com.triobank.ledger.Transaction;
import com.triobank.ledger.transaction.Channel;
import com.triobank.ledger.transaction.DefaultTitles;
import com.triobank.ledger.transaction.TransactionType;
import com.triobank.payment.dto.CashOperationRequest;
import com.triobank.payment.dto.TransactionDto;
import com.triobank.payment.dto.TransactionMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash operations at the ATM. Money goes between the customer's personal account and the bank's cash account.
 */
@Service
public class AtmService {

    static final BigDecimal MIN_AMOUNT = new BigDecimal("10.00");
    static final BigDecimal MAX_DEPOSIT = new BigDecimal("20000.00");
    static final BigDecimal MAX_WITHDRAWAL = new BigDecimal("5000.00");
    /** The ATM only takes and gives out banknotes. */
    static final BigDecimal BANKNOTE_STEP = BigDecimal.TEN;

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final BankProperties bank;

    public AtmService(AccountService accountService, LedgerService ledgerService, BankProperties bank) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.bank = bank;
    }

    /**
     * The currency the request is about: the bank currency when the client left it out.
     * The controller hashes the request with this value, so leaving it out and sending PLN is the same request.
     */
    public String resolveCurrency(String requested) {
        return PaymentRules.resolveCurrency(bank, requested);
    }

    /**
     * Puts cash on one of the customer's personal accounts.
     *
     * @param userId         who is at the ATM, stored on the transaction
     * @param idempotencyKey checked key from the header
     * @param requestHash    hash of the request, see {@link com.triobank.ledger.idempotency.RequestHash}
     * @throws ApiException RESOURCE_NOT_FOUND, CURRENCY_NOT_SUPPORTED, CASH_AMOUNT_INVALID,
     *                      OPERATION_NOT_ALLOWED_FOR_ACCOUNT, RECIPIENT_ACCOUNT_UNAVAILABLE
     */
    @Transactional
    public TransactionDto deposit(UUID userId, UUID customerId, CashOperationRequest request,
                                  String idempotencyKey, String requestHash) {
        Account account = checkCashOperation(customerId, request, MAX_DEPOSIT);

        Transaction transaction = ledgerService.post(PostingCommand.builder()
                .type(TransactionType.CASH_DEPOSIT)
                .channel(Channel.ATM)
                .sourceAccountId(bankCashId())
                .targetAccountId(account.getId())
                .amount(request.amount())
                .currency(resolveCurrency(request.currency()))
                .title(DefaultTitles.ATM_DEPOSIT)
                .senderName(DefaultTitles.BANK_NAME)
                .recipientName(PaymentRules.fullName(account.getCustomer()))
                .initiatedByUserId(userId)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .build());
        return TransactionMapper.toDto(transaction, customerId);
    }

    /**
     * Takes cash from one of the customer's personal accounts. Status and balance are checked by the ledger,
     * after the account is locked, so two withdrawals at once can't both spend the same money.
     *
     * @param userId         who is at the ATM, stored on the transaction
     * @param idempotencyKey checked key from the header
     * @param requestHash    hash of the request, see {@link com.triobank.ledger.idempotency.RequestHash}
     * @throws ApiException RESOURCE_NOT_FOUND, CURRENCY_NOT_SUPPORTED, CASH_AMOUNT_INVALID,
     *                      OPERATION_NOT_ALLOWED_FOR_ACCOUNT, SOURCE_ACCOUNT_UNAVAILABLE, INSUFFICIENT_FUNDS
     */
    @Transactional
    public TransactionDto withdraw(UUID userId, UUID customerId, CashOperationRequest request,
                                   String idempotencyKey, String requestHash) {
        Account account = checkCashOperation(customerId, request, MAX_WITHDRAWAL);

        Transaction transaction = ledgerService.post(PostingCommand.builder()
                .type(TransactionType.CASH_WITHDRAWAL)
                .channel(Channel.ATM)
                .sourceAccountId(account.getId())
                .targetAccountId(bankCashId())
                .amount(request.amount())
                .currency(resolveCurrency(request.currency()))
                .title(DefaultTitles.ATM_WITHDRAWAL)
                .senderName(PaymentRules.fullName(account.getCustomer()))
                .recipientName(DefaultTitles.BANK_NAME)
                .initiatedByUserId(userId)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .build());
        return TransactionMapper.toDto(transaction, customerId);
    }

    /** Checks shared by deposits and withdrawals, in the order the errors are reported. */
    private Account checkCashOperation(UUID customerId, CashOperationRequest request, BigDecimal max) {
        Account account = accountService.getOfCustomerForOperation(customerId, request.accountId());
        PaymentRules.checkCurrency(bank, request.currency());
        checkAmount(request.amount(), max);
        checkAtmAllowed(account);
        return account;
    }

    private UUID bankCashId() {
        return accountService.getSystemAccountId(SystemAccountCode.BANK_CASH);
    }

    private static void checkAmount(BigDecimal amount, BigDecimal max) {
        boolean inRange = amount.compareTo(MIN_AMOUNT) >= 0 && amount.compareTo(max) <= 0;
        boolean banknotes = amount.remainder(BANKNOTE_STEP).signum() == 0;
        if (!inRange || !banknotes) {
            throw new ApiException(ErrorCode.CASH_AMOUNT_INVALID,
                    "The ATM accepts multiples of 10 from " + MIN_AMOUNT.toPlainString()
                            + " to " + max.toPlainString() + ".");
        }
    }

    private static void checkAtmAllowed(Account account) {
        // the product decides; for now only personal accounts allow it
        if (!account.getProduct().isAtmAllowed()) {
            throw new ApiException(ErrorCode.OPERATION_NOT_ALLOWED_FOR_ACCOUNT,
                    "ATM operations are only available on personal accounts.");
        }
    }

}
