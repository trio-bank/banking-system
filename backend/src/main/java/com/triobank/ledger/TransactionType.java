package com.triobank.ledger;

/**
 * What kind of operation moved the money. Decided by the backend, never by the client.
 * Only the first six are used for now; the rest are kept for later payment methods.
 */
public enum TransactionType {

    OWN_TRANSFER,
    INTERNAL_TRANSFER,
    CASH_DEPOSIT,
    CASH_WITHDRAWAL,
    INTEREST,
    TAX,
    FEE,
    EXTERNAL_TRANSFER_OUT,
    EXTERNAL_TRANSFER_IN,
    PHONE_TRANSFER,
    BLIK_PAYMENT,
    BLIK_WITHDRAWAL,
    CARD_PAYMENT,
    FX_EXCHANGE

}
