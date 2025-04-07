package com.beanchainbeta.tools;

import java.math.BigDecimal;

public class beantoshinomics {
    private static final int DECIMALS = 6;

    public static long toBeantoshi(double amount) {
        return(long)(amount * Math.pow(10,DECIMALS));
    }

    public static double toBean(long beantoshiAmount) {
        return beantoshiAmount / Math.pow(10, DECIMALS);
    }

    private static final BigDecimal BEANTOSHI_MULTIPLIER = new BigDecimal("1000000");

    /**
     * Validates whether a BEAN amount is a valid transaction amount.
     * Must be a number with up to 6 decimal places and >= 1 beantoshi (0.000001 BEAN).
     *
     * @param amountStr the amount as a string (e.g., "0.00002")
     * @return true if valid, false otherwise
     */
    public static boolean isValidAmount(String amountStr) {
        try {
            BigDecimal amount = new BigDecimal(amountStr);

            // Reject if more than 6 decimal places
            if (amount.scale() > 6) {
                return false;
            }

            // Convert to beantoshi and check it's a whole number ≥ 1
            BigDecimal beantoshi = amount.multiply(BEANTOSHI_MULTIPLIER);
            return beantoshi.stripTrailingZeros().scale() <= 0 && beantoshi.compareTo(BigDecimal.ONE) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Converts a valid BEAN string amount to beantoshi (long).
     * Throws IllegalArgumentException if the amount is invalid.
     *
     * @param amountStr the BEAN amount as a string
     * @return amount in beantoshi
     */
    public static long convertToBeantoshi(String amountStr) {
        if (!isValidAmount(amountStr)) {
            throw new IllegalArgumentException("Invalid BEAN amount");
        }
        BigDecimal beantoshi = new BigDecimal(amountStr).multiply(BEANTOSHI_MULTIPLIER);
        return beantoshi.longValueExact(); // throws if overflow or fractional
    }

}
