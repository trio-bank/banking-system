package com.triobank.ledger.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.triobank.account.Account;
import com.triobank.ledger.Channel;
import com.triobank.ledger.EntryDirection;
import com.triobank.ledger.LedgerEntry;
import com.triobank.ledger.Transaction;
import com.triobank.ledger.TransactionStatus;
import com.triobank.ledger.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountHistoryMapperTest {

    private static final Instant CREATED = Instant.parse("2026-11-20T10:15:30Z");
    private static final String ANNA_IBAN = "PL45999000121000000000000001";
    private static final String JAN_IBAN = "PL18999000121000000000000002";

    private final Account anna = customerAccount(ANNA_IBAN);
    private final Account jan = customerAccount(JAN_IBAN);
    private final Account bankCash = systemAccount();

    @Test
    void debitIsOutgoingToTheRecipient() {
        Transaction transfer = transaction(anna, jan, "Anna Nowak", "Jan Kowalski");
        LedgerEntry entry = entry(transfer, EntryDirection.DEBIT, "850.00");

        AccountHistoryItemDto item = AccountHistoryMapper.toDto(entry);

        assertThat(item.entryId()).isEqualTo(entry.getId());
        assertThat(item.transactionId()).isEqualTo(transfer.getId());
        assertThat(item.reference()).isEqualTo("TB202611200000000001");
        assertThat(item.type()).isEqualTo(TransactionType.INTERNAL_TRANSFER);
        assertThat(item.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(item.channel()).isEqualTo(Channel.WEB);
        assertThat(item.direction()).isEqualTo(Direction.OUTGOING);
        assertThat(item.amount()).isEqualByComparingTo("150.00");
        assertThat(item.currency()).isEqualTo("PLN");
        assertThat(item.balanceAfter()).isEqualByComparingTo("850.00");
        assertThat(item.title()).isEqualTo("Dinner");
        assertThat(item.counterpartyName()).isEqualTo("Jan Kowalski");
        assertThat(item.counterpartyIban()).isEqualTo(JAN_IBAN);
        assertThat(item.createdAt()).isEqualTo(CREATED);
        assertThat(item.bookedAt()).isEqualTo(CREATED);
    }

    @Test
    void creditIsIncomingFromTheSender() {
        Transaction transfer = transaction(anna, jan, "Anna Nowak", "Jan Kowalski");

        AccountHistoryItemDto item = AccountHistoryMapper.toDto(entry(transfer, EntryDirection.CREDIT, "150.00"));

        assertThat(item.direction()).isEqualTo(Direction.INCOMING);
        assertThat(item.counterpartyName()).isEqualTo("Anna Nowak");
        assertThat(item.counterpartyIban()).isEqualTo(ANNA_IBAN);
    }

    @Test
    void bankAccountIsShownAsTrioBankWithoutIban() {
        Transaction deposit = transaction(bankCash, anna, "Cash desk", "Anna Nowak");
        Transaction withdrawal = transaction(anna, bankCash, "Anna Nowak", "Cash desk");

        AccountHistoryItemDto in = AccountHistoryMapper.toDto(entry(deposit, EntryDirection.CREDIT, "150.00"));
        AccountHistoryItemDto out = AccountHistoryMapper.toDto(entry(withdrawal, EntryDirection.DEBIT, "0.00"));

        assertThat(in.counterpartyName()).isEqualTo("Trio Bank");
        assertThat(in.counterpartyIban()).isNull();
        assertThat(out.counterpartyName()).isEqualTo("Trio Bank");
        assertThat(out.counterpartyIban()).isNull();
    }

    private static Account customerAccount(String iban) {
        Account account = mock(Account.class);
        when(account.getIban()).thenReturn(iban);
        return account;
    }

    private static Account systemAccount() {
        Account account = mock(Account.class);
        when(account.isSystem()).thenReturn(true);
        when(account.getIban()).thenReturn("PL00999000120000000000000001");
        return account;
    }

    private static Transaction transaction(Account source, Account target, String sender, String recipient) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(UUID.randomUUID());
        when(transaction.getReference()).thenReturn("TB202611200000000001");
        when(transaction.getType()).thenReturn(TransactionType.INTERNAL_TRANSFER);
        when(transaction.getStatus()).thenReturn(TransactionStatus.COMPLETED);
        when(transaction.getChannel()).thenReturn(Channel.WEB);
        when(transaction.getCurrency()).thenReturn("PLN");
        when(transaction.getTitle()).thenReturn("Dinner");
        when(transaction.getSourceAccount()).thenReturn(source);
        when(transaction.getTargetAccount()).thenReturn(target);
        when(transaction.getSenderName()).thenReturn(sender);
        when(transaction.getRecipientName()).thenReturn(recipient);
        when(transaction.getBookedAt()).thenReturn(CREATED);
        return transaction;
    }

    private static LedgerEntry entry(Transaction transaction, EntryDirection direction, String balanceAfter) {
        LedgerEntry entry = mock(LedgerEntry.class);
        when(entry.getId()).thenReturn(UUID.randomUUID());
        when(entry.getTransaction()).thenReturn(transaction);
        when(entry.getDirection()).thenReturn(direction);
        when(entry.getAmount()).thenReturn(new BigDecimal("150.00"));
        when(entry.getBalanceAfter()).thenReturn(new BigDecimal(balanceAfter));
        when(entry.getCreatedAt()).thenReturn(CREATED);
        return entry;
    }

}
