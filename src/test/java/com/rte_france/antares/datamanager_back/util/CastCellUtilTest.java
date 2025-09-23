package com.rte_france.antares.datamanager_back.util;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CastCellUtilTest {

    @Test
    void castDouble_throwsBusinessException_onNonNumericString() {
        String nonNumeric = "abc";
        String column = "min_stable_generation";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> CastCellUtil.castDouble(nonNumeric, column));

        assertEquals("The value '" + nonNumeric + "' in column '" + column + "' is not numeric", ex.getMessage());
    }
}
