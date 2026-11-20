package com.triobank.payment.dto;

import com.triobank.account.Account;
import com.triobank.ledger.Transaction;
import java.util.UUID;

public final class TransactionMapper {

    private TransactionMapper() {
    }

    /**
     * The same transaction looks different to its two sides: {@code ownAccount} is set
     * on the side that belongs to {@code customerId}. Must run inside a transaction, the accounts are lazy.
     */
    public static TransactionDto toDto(Transaction transaction, UUID customerId) {
        return new TransactionDto(
                transaction.getId(),
                transaction.getReference(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getChannel(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getTitle(),
                party(transaction.getSenderName(), transaction.getSourceAccount(), customerId),
                party(transaction.getRecipientName(), transaction.getTargetAccount(), customerId),
                transaction.getCreatedAt(),
                transaction.getBookedAt());
    }

    private static PartyDto party(String name, Account account, UUID customerId) {
        if (account.isSystem()) {
            return new PartyDto(name, null, false);
        }
        boolean own = account.getCustomer().getId().equals(customerId);
        return new PartyDto(name, account.getIban(), own);
    }

}
