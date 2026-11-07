package com.triobank.common.iban;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks Polish IBANs: "PL" followed by 26 digits, with check digits that add up (mod 97 = 1).
 * Spaces and lower case in the input are fine; use {@link #normalize} before storing.
 */
public final class IbanValidator {

    public enum Result {
        VALID,
        INVALID_FORMAT,
        INVALID_CHECKSUM
    }

    private static final Pattern POLISH_IBAN = Pattern.compile("PL\\d{26}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private IbanValidator() {
    }

    /**
     * Removes spaces and upper-cases the letters, e.g. {@code "pl45 9990 …"} becomes {@code "PL459990…"}.
     */
    public static String normalize(String input) {
        return input == null ? null : WHITESPACE.matcher(input).replaceAll("").toUpperCase(Locale.ROOT);
    }

    public static Result check(String input) {
        String iban = normalize(input);
        if (iban == null || !POLISH_IBAN.matcher(iban).matches()) {
            return Result.INVALID_FORMAT;
        }
        return mod97(iban.substring(4) + iban.substring(0, 4)) == 1 ? Result.VALID : Result.INVALID_CHECKSUM;
    }

    public static boolean isValid(String input) {
        return check(input) == Result.VALID;
    }

    /**
     * Remainder of dividing the number by 97, where letters count as two digits (A = 10 … Z = 35).
     * Goes digit by digit, because the whole number does not fit into a long.
     */
    static int mod97(String digitsAndLetters) {
        int remainder = 0;
        for (int i = 0; i < digitsAndLetters.length(); i++) {
            int value = Character.digit(digitsAndLetters.charAt(i), 36);
            remainder = (value < 10 ? remainder * 10 : remainder * 100) + value;
            remainder %= 97;
        }
        return remainder;
    }

}
