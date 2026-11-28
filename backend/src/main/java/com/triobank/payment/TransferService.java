package com.triobank.payment;

import com.triobank.account.Account;
import com.triobank.account.AccountService;
import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.common.error.FieldErrorCode;
import com.triobank.ledger.LedgerService;
import com.triobank.ledger.PostingCommand;
import com.triobank.ledger.Transaction;
import com.triobank.ledger.transaction.Channel;
import com.triobank.ledger.transaction.DefaultTitles;
import com.triobank.ledger.transaction.TransactionType;
import com.triobank.payment.dto.TransactionDto;
import com.triobank.payment.dto.TransactionMapper;
import com.triobank.payment.dto.TransferRequest;
import com.triobank.product.OutgoingTransferPolicy;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transfers from one of the customer's accounts to any customer account in Trio Bank, found by IBAN.
 * The backend decides the type: to the customer's own account it is an own transfer, otherwise an
 * internal one. Rules are checked in a fixed order and the first broken one is reported.
 */
@Service
public class TransferService {

    /** Where the sort code sits in a Polish IBAN: after "PL" and the two check digits. */
    private static final int SORT_CODE_START = 4;
    private static final int SORT_CODE_END = 12;

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final BankProperties bank;

    public TransferService(AccountService accountService, LedgerService ledgerService, BankProperties bank) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.bank = bank;
    }

    /**
     * The currency the request is about: the bank currency when the client left it out.
     * The controller hashes the request with this value.
     */
    public String resolveCurrency(String requested) {
        return PaymentRules.resolveCurrency(bank, requested);
    }

    /**
     * Sends the money. Account statuses and the balance are checked by the ledger after both accounts
     * are locked, so two transfers at once can't spend the same money.
     *
     * @param userId         who sends it, stored on the transaction
     * @param idempotencyKey checked key from the header
     * @param requestHash    hash of the request, see {@link com.triobank.ledger.idempotency.RequestHash}
     * @throws ApiException RESOURCE_NOT_FOUND, CURRENCY_NOT_SUPPORTED, EXTERNAL_TRANSFER_NOT_SUPPORTED,
     *                      RECIPIENT_ACCOUNT_NOT_FOUND, SAME_ACCOUNT_TRANSFER, VALIDATION_ERROR,
     *                      TRANSFER_POLICY_VIOLATION, SOURCE_ACCOUNT_UNAVAILABLE, RECIPIENT_ACCOUNT_UNAVAILABLE,
     *                      INSUFFICIENT_FUNDS
     */
    @Transactional
    public TransactionDto transfer(UUID userId, UUID customerId, TransferRequest request,
                                   String idempotencyKey, String requestHash) {
        Account source = accountService.getOfCustomerForOperation(customerId, request.sourceAccountId());
        PaymentRules.checkCurrency(bank, request.currency());
        checkOurBank(request.targetIban());
        Account target = accountService.findForTransfer(request.targetIban())
                .orElseThrow(() -> new ApiException(ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND,
                        "There is no account with this IBAN in Trio Bank."));
        if (source.getId().equals(target.getId())) {
            throw new ApiException(ErrorCode.SAME_ACCOUNT_TRANSFER, "Source and target must be different accounts.");
        }

        boolean own = target.getCustomer().getId().equals(customerId);
        String senderName = PaymentRules.fullName(source.getCustomer());
        String recipientName;
        if (own) {
            // the customer's own name is used, whatever the request says
            recipientName = senderName;
        } else {
            recipientName = requireRecipientName(request.recipientName());
            checkPolicyAllowsOthers(source);
        }
        TransactionType type = own ? TransactionType.OWN_TRANSFER : TransactionType.INTERNAL_TRANSFER;

        Transaction transaction = ledgerService.post(PostingCommand.builder()
                .type(type)
                .channel(Channel.WEB)
                .sourceAccountId(source.getId())
                .targetAccountId(target.getId())
                .amount(request.amount())
                .currency(resolveCurrency(request.currency()))
                .title(request.title() != null ? request.title() : defaultTitle(type))
                .senderName(senderName)
                .recipientName(recipientName)
                .initiatedByUserId(userId)
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .build());
        return TransactionMapper.toDto(transaction, customerId);
    }

    private void checkOurBank(String iban) {
        if (!iban.substring(SORT_CODE_START, SORT_CODE_END).equals(bank.sortCode())) {
            throw new ApiException(ErrorCode.EXTERNAL_TRANSFER_NOT_SUPPORTED,
                    "Transfers to other banks are not supported yet.");
        }
    }

    private static String requireRecipientName(String recipientName) {
        if (recipientName == null) {
            throw ApiException.fieldError("recipientName", FieldErrorCode.REQUIRED,
                    "Recipient name is required for a transfer to another customer.");
        }
        return recipientName;
    }

    private static void checkPolicyAllowsOthers(Account source) {
        if (source.getProduct().getOutgoingTransferPolicy() == OutgoingTransferPolicy.OWN_ACCOUNTS_ONLY) {
            throw new ApiException(ErrorCode.TRANSFER_POLICY_VIOLATION,
                    "Money from this account can only go to your own accounts.");
        }
    }

    private static String defaultTitle(TransactionType type) {
        return type == TransactionType.OWN_TRANSFER ? DefaultTitles.OWN_TRANSFER : DefaultTitles.INTERNAL_TRANSFER;
    }

}
