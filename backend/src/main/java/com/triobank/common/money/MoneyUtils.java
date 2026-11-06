package com.triobank.common.money;

import java.math.BigDecimal;

/**
 * Helpers to keep amounts at 2 decimals everywhere, e.g. {@code 10.00} instead of {@code 10}.
 */
public final class MoneyUtils {

    private MoneyUtils() {
    }

    /** {@code "10"} → {@code 10.00}, {@code "10.5"} → {@code 10.50}. */
    public static BigDecimal of(String amount) {
        return normalize(new BigDecimal(amount));
    }

    /** Sets the scale to 2, rounding half-even if needed. */
    public static BigDecimal normalize(BigDecimal amount) {
        return amount == null ? null : amount.setScale(MoneyConstants.SCALE, MoneyConstants.ROUNDING);
    }

}
