package com.triobank.account;

/**
 * The account money leaves and the account it goes to.
 */
public record PostingAccounts(Account source, Account target) {
}
