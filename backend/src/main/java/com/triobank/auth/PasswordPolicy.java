package com.triobank.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Password rules: 12–128 characters, at least one lowercase letter, one uppercase letter, one digit and one special
 * character, and no part of the email before "@". The frontend checks the same rules in password.validator.ts,
 * so keep both in sync.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Pattern LOWERCASE = Pattern.compile("\\p{Ll}");
    private static final Pattern UPPERCASE = Pattern.compile("\\p{Lu}");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    /** Anything that is not a letter, a digit or whitespace. */
    private static final Pattern SPECIAL = Pattern.compile("[^\\p{L}0-9\\p{IsWhite_Space}\\uFEFF]");
    /** Shorter names like "a@x.pl" are ignored, otherwise almost no password would pass. */
    private static final int MIN_EMAIL_PART_TO_CHECK = 3;

    private PasswordPolicy() {
    }

    public static boolean isStrong(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH
                && LOWERCASE.matcher(password).find()
                && UPPERCASE.matcher(password).find()
                && DIGIT.matcher(password).find()
                && SPECIAL.matcher(password).find();
    }

    public static boolean isStrong(String password, String email) {
        return isStrong(password) && !containsEmailName(password, email);
    }

    static boolean containsEmailName(String password, String email) {
        if (password == null || email == null) {
            return false;
        }
        String name = email.split("@", 2)[0].trim().toLowerCase(Locale.ROOT);
        return name.length() >= MIN_EMAIL_PART_TO_CHECK && password.toLowerCase(Locale.ROOT).contains(name);
    }

}
