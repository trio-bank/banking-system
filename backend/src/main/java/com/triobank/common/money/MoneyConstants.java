package com.triobank.common.money;

import java.math.RoundingMode;

/**
 * Scale and rounding for money.
 */
public final class MoneyConstants {

    /** Balances and amounts. */
    public static final int SCALE = 2;
    /** Interest that is accrued but not yet paid out. */
    public static final int ACCRUAL_SCALE = 6;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private MoneyConstants() {
    }

}
