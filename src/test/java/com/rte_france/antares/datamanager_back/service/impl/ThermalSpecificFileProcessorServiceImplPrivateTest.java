package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalSpecificFileProcessorServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThermalSpecificFileProcessorServiceImplPrivateTest {

    private static String invokeToExcelColumn(int index0Based) {
        try {
            Class<?> clazz = ThermalSpecificFileProcessorServiceImpl.class;
            java.lang.reflect.Method m = clazz.getDeclaredMethod("toExcelColumn", int.class);
            m.setAccessible(true);
            Object result = m.invoke(null, index0Based);
            return (String) result;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void toExcelColumn_basicLetters() {
        assertEquals("A", invokeToExcelColumn(0));
        assertEquals("B", invokeToExcelColumn(1));
        assertEquals("Z", invokeToExcelColumn(25));
    }

    @Test
    void toExcelColumn_doubleLetters() {
        assertEquals("AA", invokeToExcelColumn(26));
        assertEquals("AZ", invokeToExcelColumn(51));
        assertEquals("BA", invokeToExcelColumn(52));
    }

    @Test
    void toExcelColumn_edgeCases() {
        assertEquals("ZZ", invokeToExcelColumn(701));
        assertEquals("AAA", invokeToExcelColumn(702));
    }
}
