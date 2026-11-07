package com.triobank.common.pesel;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Reads the date of birth from a PESEL ({@code YYMMDD…}). The century is hidden in the month:
 * people born in 2000–2099 have 20 added to the month, 1800–1899 have 80 added, and so on.
 */
public final class PeselParser {

    /** Century for month offsets 0, 20, 40, 60 and 80. */
    private static final int[] CENTURIES = {1900, 2000, 2100, 2200, 1800};

    private PeselParser() {
    }

    /**
     * @param pesel a PESEL that passed {@link PeselValidator}
     * @throws IllegalArgumentException if there is no real date inside
     */
    public static LocalDate birthDate(String pesel) {
        return tryBirthDate(pesel)
                .orElseThrow(() -> new IllegalArgumentException("PESEL does not contain a valid date of birth"));
    }

    static Optional<LocalDate> tryBirthDate(String pesel) {
        if (pesel == null || pesel.length() < 6) {
            return Optional.empty();
        }
        try {
            int year = Integer.parseInt(pesel.substring(0, 2));
            int encodedMonth = Integer.parseInt(pesel.substring(2, 4));
            int day = Integer.parseInt(pesel.substring(4, 6));
            int month = encodedMonth % 20;
            return Optional.of(LocalDate.of(CENTURIES[encodedMonth / 20] + year, month, day));
        } catch (NumberFormatException | DateTimeException ex) {
            return Optional.empty();
        }
    }

}
