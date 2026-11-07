package com.triobank.common.pesel;

import java.util.regex.Pattern;

/**
 * Checks a PESEL: 11 digits, a correct check digit and a real date of birth inside.
 */
public final class PeselValidator {

    public enum Result {
        VALID,
        INVALID_FORMAT,
        INVALID_CHECKSUM,
        INVALID_DATE
    }

    private static final Pattern ELEVEN_DIGITS = Pattern.compile("\\d{11}");
    private static final int[] WEIGHTS = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};

    private PeselValidator() {
    }

    public static Result check(String pesel) {
        if (pesel == null || !ELEVEN_DIGITS.matcher(pesel).matches()) {
            return Result.INVALID_FORMAT;
        }
        if (!hasValidCheckDigit(pesel)) {
            return Result.INVALID_CHECKSUM;
        }
        if (PeselParser.tryBirthDate(pesel).isEmpty()) {
            return Result.INVALID_DATE;
        }
        return Result.VALID;
    }

    public static boolean isValid(String pesel) {
        return check(pesel) == Result.VALID;
    }

    private static boolean hasValidCheckDigit(String pesel) {
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (pesel.charAt(i) - '0') * WEIGHTS[i];
        }
        return (10 - sum % 10) % 10 == pesel.charAt(10) - '0';
    }

}
