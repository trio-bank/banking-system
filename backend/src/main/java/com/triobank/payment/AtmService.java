package com.triobank.payment;

import com.triobank.account.Account;
import com.triobank.account.AccountService;
import com.triobank.account.SystemAccountCode;
import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.customer.Customer;
import com.triobank.ledger.Channel;
import com.triobank.ledger.DefaultTitles;
import com.triobank.ledger.LedgerService;
import com.triobank.ledger.PostingCommand;
import com.triobank.ledger.Transaction;
import com.triobank.ledger.TransactionType;
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
        return requested == null ? bank.currency() : requested;
    }

    /**
     * Puts cash on one of the customer's personal accounts.
     *
     * @param userId         who is at the ATM, stored on the transaction
     * @param idempotencyKey checked key from the header
     * @param requestHash    hash of the request, see {@link com.triobank.ledger.RequestHash}
     * @throws ApiException RESOURCE_NOT_FOUND, CURRENCY_NOT_SUPPORTED, CASH_AMOUNT_INVALID,
     *                      OPERATION_NOT_ALLOWED_FOR_ACCOUNT, RECIPIENT_ACCOUNT_UNAVAILABLE
     */
    @Transactional
    public TransactionDto deposit(UUID userId, UUID customerId, CashOperationRequest request,
                                  String idempotencyKey, String requestHash) {
        Account account = accountService.getOfCustomerForOperation(customerId, request.accountId());
        String currency = checkCurrency(request.currency());
        checkAmount(request.amount(), MAX_DEPOSIT);
        checkAtmAllowed(account);

        Customer owner = account.getCustomer();
        Transaction transaction = ledgerService.post(PostingCommand.builder()
                .type(TransactionType.CASH_DEPOSIT)
                .channel(Channel.ATM)
                .sourceAccountId(accountService.getSystemAccountId(SystemAccountCode.BANK_CASH))
                .targetAccountId(account.getId())
                .amount(request.amount())
                .currency(currency)
                .title(DefaultTitles.ATM_DEPOSIT)
                .senderName(DefaultTitles.BANK_NAME)
                .recipientName(owner.getFirstName() + " " + owner.getLastName())
                .initiatedByUserId(userId)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .build());
        return TransactionMapper.toDto(transaction, customerId);
    }

    private String checkCurrency(String requested) {
        String currency = resolveCurrency(requested);
        if (!currency.equals(bank.currency())) {
            throw new ApiException(ErrorCode.CURRENCY_NOT_SUPPORTED,
                    "Only " + bank.currency() + " is supported.");
        }
        return currency;
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
