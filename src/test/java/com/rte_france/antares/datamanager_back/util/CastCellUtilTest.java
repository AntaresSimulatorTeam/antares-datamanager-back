package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CastCellUtilTest {

    @Test
    void castDouble_throwsBusinessException_onNonNumericString() {
        String nonNumeric = "abc";
        String column = "min_stable_generation";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> CastCellUtil.castDouble(nonNumeric, column, 1));

        assertEquals("The value '" + nonNumeric + "' in column '" + column + "' at row 2 is not numeric", ex.getMessage());
    }

    @Test
    void shouldReturnNull_whenValueIsNull() {
        assertNull(CastCellUtil.castBigDecimal(null, "my_column", 1));
    }

    @Test
    void shouldReturnNull_whenValueIsEmptyString() {
        assertNull(CastCellUtil.castBigDecimal("", "my_column", 1));
    }

    @Test
    void shouldReturnSameBigDecimal_whenValueIsBigDecimal() {
        BigDecimal expected = new BigDecimal("123.45");
        BigDecimal result = CastCellUtil.castBigDecimal(expected, "my_column", 1);
        assertEquals(expected, result);
    }

    @Test
    void shouldReturnBigDecimal_whenValueIsDouble() {
        Double input = 12.34;
        BigDecimal result = CastCellUtil.castBigDecimal(input, "my_column", 1);
        assertEquals(BigDecimal.valueOf(12.34), result);
    }

    @Test
    void shouldReturnBigDecimal_whenValueIsOtherNumberType() {
        Integer input = 42;
        BigDecimal result = CastCellUtil.castBigDecimal(input, "my_column", 1);
        assertEquals(BigDecimal.valueOf(42.0), result);
    }

    @Test
    void shouldReturnBigDecimal_whenValueIsAValidStringNumber() {
        String input = "99.99";
        BigDecimal result = CastCellUtil.castBigDecimal(input, "my_column", 1);
        assertEquals(new BigDecimal("99.99"), result);
    }

    @Test
    void shouldThrowBusinessException_whenValueIsInvalidString() {
        String invalidInput = "not-a-number";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> CastCellUtil.castBigDecimal(invalidInput, "my_column", 1));

        assertTrue(ex.getMessage().contains("Invalid BigDecimal value for column '" + "my_column"));
    }

    @Test
    void shouldThrowBusinessException_whenValueIsUnsupportedType() {
        Boolean unsupportedInput = true;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> CastCellUtil.castBigDecimal(unsupportedInput, "my_column", 1));

        assertTrue(ex.getMessage().contains("Invalid BigDecimal value for column '" + "my_column"));
    }
}
