package com.rte_france.antares.datamanager_back.util;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Parses numeric values from text, accepting both ',' and '.' as decimal separator.
 */
public final class DecimalUtils {

    private DecimalUtils() {
    }

    /**
     * Based on {@link Double#parseDouble}: also accepts "Infinity", "-Infinity" and "NaN".
     *
     * @throws NumberFormatException if the value is null or not a valid number
     */
    public static double parseDouble(String value) {
        if (value == null) {
            throw new NumberFormatException("Cannot parse null as a number");
        }
        return Double.parseDouble(value.replace(',', '.'));
    }

    public static boolean isDouble(String value) {
        if (value == null) {
            return false;
        }
        try {
            parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Strict, exact parsing: rejects "Infinity" and "NaN".
     *
     * @throws NumberFormatException if the value is null, blank or not a valid decimal
     */
    public static BigDecimal parseDecimal(String value) {
        if (value == null) {
            throw new NumberFormatException("Cannot parse null as a decimal");
        }
        return new BigDecimal(value.trim().replace(',', '.'));
    }

    public static boolean isDecimal(String value) {
        return tryParseDecimal(value).isPresent();
    }

    public static Optional<BigDecimal> tryParseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseDecimal(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
