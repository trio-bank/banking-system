package com.triobank.ledger.transaction;

/**
 * Where the operation came from. SYSTEM is used by scheduled jobs; MOBILE and API aren't used yet.
 */
public enum Channel {

    WEB,
    ATM,
    BRANCH,
    SYSTEM,
    MOBILE,
    API

}
