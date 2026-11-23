package com.triobank.ledger;

import com.triobank.account.Account;
import com.triobank.account.AccountService;
import com.triobank.account.PostingAccounts;
import com.triobank.audit.AuditAction;
import com.triobank.audit.AuditEventCommand;
import com.triobank.audit.AuditService;
import com.triobank.auth.Role;
import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.common.money.MoneyUtils;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place where money moves. Every posting changes two balances and writes one transaction
 * with a debit and a credit entry, all in the caller's database transaction.
 * Rules of a particular operation (ATM limits, transfer policy, ownership) are checked by the caller first.
 */
@Service
public class LedgerService {

    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AccountService accountService;
    private final TransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final AuditService auditService;
    private final BankProperties bank;
    private final Clock clock;

    public LedgerService(AccountService accountService, TransactionRepository transactions,
                         LedgerEntryRepository entries, AuditService auditService, BankProperties bank,
                         Clock clock) {
        this.accountService = accountService;
        this.transactions = transactions;
        this.entries = entries;
        this.auditService = auditService;
        this.bank = bank;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Transaction post(PostingCommand command) {
        if (command.sourceAccountId().equals(command.targetAccountId())) {
            throw new ApiException(ErrorCode.SAME_ACCOUNT_TRANSFER, "Source and target must be different accounts.");
        }
        PostingAccounts accounts = accountService.getForPosting(command.sourceAccountId(), command.targetAccountId());
        Account source = accounts.source();
        Account target = accounts.target();
        BigDecimal amount = MoneyUtils.normalize(command.amount());

        checkCanPost(command, source, target, amount);

        source.debit(amount);
        target.credit(amount);

        // The database keeps microseconds, so use the same value that is stored.
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Transaction transaction = transactions.save(Transaction.builder()
                .reference(nextReference(now))
                .type(command.type())
                .status(TransactionStatus.COMPLETED)
                .channel(command.channel())
                .amount(amount)
                .currency(command.currency())
                .sourceAccount(source)
                .targetAccount(target)
                .senderName(command.senderName())
                .recipientName(command.recipientName())
                .title(command.title())
                .initiatedByUserId(command.initiatedByUserId())
                .idempotencyKey(command.idempotencyKey())
                .requestHash(command.requestHash())
                .createdAt(now)
                .bookedAt(now)
                .build());
        entries.saveAll(List.of(
                LedgerEntry.of(transaction, source, EntryDirection.DEBIT, amount, source.getBalance(), now),
                LedgerEntry.of(transaction, target, EntryDirection.CREDIT, amount, target.getBalance(), now)));

        recordPosted(transaction);
        return transaction;
    }

    private static void checkCanPost(PostingCommand command, Account source, Account target, BigDecimal amount) {
        if (!source.isActive()) {
            throw new ApiException(ErrorCode.SOURCE_ACCOUNT_UNAVAILABLE, "The source account is not active.");
        }
        if (!target.isActive()) {
            throw new ApiException(ErrorCode.RECIPIENT_ACCOUNT_UNAVAILABLE, "The recipient account is not active.");
        }
        if (!command.currency().equals(source.getCurrency()) || !command.currency().equals(target.getCurrency())) {
            throw new ApiException(ErrorCode.CURRENCY_NOT_SUPPORTED,
                    "Currency " + command.currency() + " does not match the accounts.");
        }
        // system accounts may go below zero, customer accounts never
        if (!source.isSystem() && source.getBalance().compareTo(amount) < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS, "Not enough money on the account.");
        }
    }

    /** TB + bank date + 10-digit running number, e.g. TB202610240000000123. */
    private String nextReference(Instant now) {
        String date = REFERENCE_DATE.format(now.atZone(bank.timeZone()));
        return "TB" + date + String.format("%010d", transactions.nextReferenceNumber());
    }

    private void recordPosted(Transaction transaction) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reference", transaction.getReference());
        details.put("type", transaction.getType().name());
        details.put("channel", transaction.getChannel().name());
        details.put("amount", transaction.getAmount().toPlainString());
        details.put("currency", transaction.getCurrency());
        details.put("sourceAccountId", transaction.getSourceAccount().getId().toString());
        details.put("targetAccountId", transaction.getTargetAccount().getId().toString());

        auditService.record(AuditEventCommand.builder()
                .action(AuditAction.TRANSACTION_POSTED)
                .actorUserId(transaction.getInitiatedByUserId())
                .actorRole(actorRole(transaction))
                .targetType("TRANSACTION")
                .targetId(transaction.getId())
                .details(details)
                .build());
    }

    /** Branch operations are done by an admin, jobs by the system, everything else by the customer. */
    private static String actorRole(Transaction transaction) {
        if (transaction.getInitiatedByUserId() == null) {
            return "SYSTEM";
        }
        return transaction.getChannel() == Channel.BRANCH ? Role.ADMIN.name() : Role.CUSTOMER.name();
    }

}
