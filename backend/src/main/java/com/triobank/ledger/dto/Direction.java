package com.triobank.ledger.dto;

import com.triobank.ledger.EntryDirection;

/**
 * Which way money went from the point of view of the account whose history is shown.
 */
public enum Direction {

    INCOMING,
    OUTGOING;

    public static Direction of(EntryDirection entryDirection) {
        return entryDirection == EntryDirection.CREDIT ? INCOMING : OUTGOING;
    }

}
