package com.triobank.payment;

import com.triobank.common.config.BankProperties;
import com.triobank.common.error.ApiException;
import com.triobank.common.error.ErrorCode;
import com.triobank.customer.Customer;

/**
 * Small rules shared by the ATM and transfers.
 */
final class PaymentRules {

    private PaymentRules() {
    }

    /** The bank currency when the client left the currency out. */
    static String resolveCurrency(BankProperties bank, String requested) {
        return requested == null ? bank.currency() : requested;
    }

    static void checkCurrency(BankProperties bank, String requested) {
        if (!resolveCurrency(bank, requested).equals(bank.currency())) {
            throw new ApiException(ErrorCode.CURRENCY_NOT_SUPPORTED, "Only " + bank.currency() + " is supported.");
        }
    }

    /** Name stored on the transaction for a customer's side of it. */
    static String fullName(Customer customer) {
        return customer.getFirstName() + " " + customer.getLastName();
    }

}
