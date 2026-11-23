package com.triobank.ledger;

/**
 * Titles and names the backend fills in when the client leaves them out.
 * Interest and tax titles contain the month and will be added together with the interest jobs.
 */
public final class DefaultTitles {

    /** Shown as the other party when money comes from or goes to one of the bank's own accounts. */
    public static final String BANK_NAME = "Trio Bank";

    public static final String OWN_TRANSFER = "Own transfer";
    public static final String INTERNAL_TRANSFER = "Funds transfer";
    public static final String ATM_DEPOSIT = "ATM cash deposit";
    public static final String ATM_WITHDRAWAL = "ATM cash withdrawal";
    public static final String BRANCH_DEPOSIT = "Branch cash deposit";
    public static final String BRANCH_WITHDRAWAL = "Branch cash withdrawal";

    private DefaultTitles() {
    }

}
