package com.triobank.ledger.dto;

import com.triobank.account.Account;
import com.triobank.ledger.DefaultTitles;
import com.triobank.ledger.LedgerEntry;
import com.triobank.ledger.Transaction;

public final class AccountHistoryMapper {

    private AccountHistoryMapper() {
    }

    /**
     * The counterparty is the other side of the transaction. Its name comes from the transaction itself,
     * so a later change of the customer's name does not rewrite old history.
     */
    public static AccountHistoryItemDto toDto(LedgerEntry entry) {
        Transaction transaction = entry.getTransaction();
        Direction direction = Direction.of(entry.getDirection());
        boolean incoming = direction == Direction.INCOMING;
        Account counterparty = incoming ? transaction.getSourceAccount() : transaction.getTargetAccount();

        String counterpartyName;
        String counterpartyIban;
        if (counterparty.isSystem()) {
            counterpartyName = DefaultTitles.BANK_NAME;
            counterpartyIban = null;
        } else {
            counterpartyName = incoming ? transaction.getSenderName() : transaction.getRecipientName();
            counterpartyIban = counterparty.getIban();
        }

        return new AccountHistoryItemDto(
                entry.getId(),
                transaction.getId(),
                transaction.getReference(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getChannel(),
                direction,
                entry.getAmount(),
                transaction.getCurrency(),
                entry.getBalanceAfter(),
                transaction.getTitle(),
                counterpartyName,
                counterpartyIban,
                entry.getCreatedAt(),
                transaction.getBookedAt());
    }

}
