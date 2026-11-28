package com.triobank.ledger;

import com.triobank.common.web.PageResponse;
import com.triobank.ledger.dto.AccountHistoryItemDto;
import com.triobank.ledger.dto.AccountHistoryMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads from the ledger. Kept apart from {@link LedgerService}, which needs the account module,
 * so that the account module can use this one without a circular dependency.
 */
@Service
public class LedgerQueryService {

    private final LedgerEntryRepository entries;
    private final TransactionRepository transactions;

    public LedgerQueryService(LedgerEntryRepository entries, TransactionRepository transactions) {
        this.entries = entries;
        this.transactions = transactions;
    }

    /**
     * History of one account, newest first. Does not check who owns the account; the caller does that.
     */
    @Transactional(readOnly = true)
    public PageResponse<AccountHistoryItemDto> getAccountHistory(UUID accountId, int page, int size) {
        return PageResponse.from(entries.findHistoryOfAccount(accountId, PageRequest.of(page, size)),
                AccountHistoryMapper::toDto);
    }

    /**
     * Transaction the user already sent with this idempotency key, if any.
     */
    @Transactional(readOnly = true)
    public Optional<Transaction> findByIdempotencyKey(UUID userId, String idempotencyKey) {
        return transactions.findByInitiatedByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

}
