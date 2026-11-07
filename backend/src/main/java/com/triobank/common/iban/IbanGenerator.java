package com.triobank.common.iban;

import com.triobank.common.config.BankProperties;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Builds IBANs of our bank: "PL" + 2 check digits + sort code + 16-digit account number.
 */
@Component
public class IbanGenerator {

    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\d{16}");
    private static final int[] SORT_CODE_WEIGHTS = {3, 9, 7, 1, 3, 9, 7};

    private final String sortCode;

    public IbanGenerator(BankProperties bank) {
        if (!isValidSortCode(bank.sortCode())) {
            throw new IllegalStateException("bank.sort-code " + bank.sortCode() + " has a wrong check digit");
        }
        this.sortCode = bank.sortCode();
    }

    /**
     * @param accountNumber 16 digits, e.g. from the account number sequence
     */
    public String generate(String accountNumber) {
        if (accountNumber == null || !ACCOUNT_NUMBER.matcher(accountNumber).matches()) {
            throw new IllegalArgumentException("Account number must have 16 digits");
        }
        String bban = sortCode + accountNumber;
        int checkDigits = 98 - IbanValidator.mod97(bban + "PL00");
        return "PL%02d%s".formatted(checkDigits, bban);
    }

    /** The last digit of a Polish sort code is a check digit over the first seven. */
    static boolean isValidSortCode(String sortCode) {
        if (sortCode == null || !sortCode.matches("\\d{8}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < SORT_CODE_WEIGHTS.length; i++) {
            sum += (sortCode.charAt(i) - '0') * SORT_CODE_WEIGHTS[i];
        }
        return (10 - sum % 10) % 10 == sortCode.charAt(7) - '0';
    }

}
