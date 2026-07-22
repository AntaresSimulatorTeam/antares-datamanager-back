package com.rte_france.antares.datamanager_back.service.settings;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParameterValueConverterTest {

    @Test
    void testGetStringValueWithValidString() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("mode", "Adequacy");

        String result = ParameterValueConverter.getStringValue(dataMap, "Mode");
        assertEquals("Adequacy", result);
    }

    @Test
    void testGetStringValueWithNullValue() {
        Map<String, Object> dataMap = new HashMap<>();

        String result = ParameterValueConverter.getStringValue(dataMap, "NonExistent");
        assertEquals("", result);
    }

    @Test
    void testGetStringValueWithTrim() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("mode", "  Adequacy  ");

        String result = ParameterValueConverter.getStringValue(dataMap, "Mode");
        assertEquals("Adequacy", result);
    }

    @Test
    void testGetStringValueWithSpacesInKey() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number-of-mc-year", "100 MC");

        String result = ParameterValueConverter.getStringValue(dataMap, "Number of MC year");
        assertEquals("100 MC", result);
    }

    @Test
    void testGetStringValueWithInteger() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("mode", 123);

        String result = ParameterValueConverter.getStringValue(dataMap, "Mode");
        assertEquals("123", result);
    }

    @Test
    void testGetIntValueWithValidInteger() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number-of-mc-year", 100);

        Integer result = ParameterValueConverter.getIntValue(dataMap, "Number of MC year");
        assertEquals(100, result);
    }

    @Test
    void testGetIntValueWithStringNumber() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number-of-mc-year", "100");

        Integer result = ParameterValueConverter.getIntValue(dataMap, "Number of MC year");
        assertEquals(100, result);
    }

    @Test
    void testGetIntValueWithNullValue() {
        Map<String, Object> dataMap = new HashMap<>();

        Integer result = ParameterValueConverter.getIntValue(dataMap, "NonExistent");
        assertEquals(0, result);
    }

    @Test
    void testGetIntValueWithInvalidNumber() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number-of-mc-year", "InvalidNumber");

        Integer result = ParameterValueConverter.getIntValue(dataMap, "Number of MC year");
        assertEquals(0, result);
    }

    @Test
    void testGetIntValueWithNumberType() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number-of-mc-year", 50.5);

        Integer result = ParameterValueConverter.getIntValue(dataMap, "Number of MC year");
        assertEquals(50, result);
    }

    @Test
    void testGetIntValueWithStringNumberTrim() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number-of-mc-year", "  200  ");

        Integer result = ParameterValueConverter.getIntValue(dataMap, "Number of MC year");
        assertEquals(200, result);
    }

    @Test
    void testGetIntValueWithLongNumber() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number", 1000000L);

        Integer result = ParameterValueConverter.getIntValue(dataMap, "Number");
        assertEquals(1000000, result);
    }

    @Test
    void testGetBooleanValueWithTrue() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", true);

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertTrue(result);
    }

    @Test
    void testGetBooleanValueWithFalse() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", false);

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertFalse(result);
    }

    @Test
    void testGetBooleanValueWithStringTrue() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "true");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertTrue(result);
    }

    @Test
    void testGetBooleanValueWithStringYes() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "yes");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertTrue(result);
    }

    @Test
    void testGetBooleanValueWithString1() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "1");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertTrue(result);
    }

    @Test
    void testGetBooleanValueWithStringFalse() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "false");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertFalse(result);
    }

    @Test
    void testGetBooleanValueWithStringNo() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "no");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertFalse(result);
    }

    @Test
    void testGetBooleanValueWithString0() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "0");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertFalse(result);
    }

    @Test
    void testGetBooleanValueWithNullValue() {
        Map<String, Object> dataMap = new HashMap<>();

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "NonExistent");
        assertFalse(result);
    }

    @Test
    void testGetBooleanValueWithInvalidString() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "invalid");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertFalse(result);
    }

    @Test
    void testGetBooleanValueWithCaseInsensitive() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "TRUE");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertTrue(result);
    }

    @Test
    void testGetBooleanValueWithCaseInsensitiveYES() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "YES");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertTrue(result);
    }

    @Test
    void testGetBooleanValueWithMixedCase() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "True");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertTrue(result);
    }

    @Test
    void testGetBooleanValueWithTrimAndCase() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", "  YES  ");

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertTrue(result);
    }

    @Test
    void testGetBooleanValueWithInteger1() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", 1);

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertTrue(result);
    }

    @Test
    void testGetBooleanValueWithInteger0() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("leap-year", 0);

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");
        assertFalse(result);
    }

    @Test
    void testKeyNormalization() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("binding-constraints", true);

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "binding constraints");
        assertTrue(result);
    }

    @Test
    void testKeyNormalizationMultipleSpaces() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("thermal-clusters-min-stable-power", true);

        Boolean result = ParameterValueConverter.getBooleanValue(dataMap, "thermal clusters min stable power");
        assertTrue(result);
    }

    @Test
    void testGetStringValueWithEmptyString() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("mode", "");

        String result = ParameterValueConverter.getStringValue(dataMap, "Mode");
        assertEquals("", result);
    }

    @Test
    void testGetIntValueWithNegativeNumber() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number", -100);

        Integer result = ParameterValueConverter.getIntValue(dataMap, "Number");
        assertEquals(-100, result);
    }

    @Test
    void testGetIntValueWithNegativeStringNumber() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number", "-50");

        Integer result = ParameterValueConverter.getIntValue(dataMap, "Number");
        assertEquals(-50, result);
    }

    @Test
    void testGetIntValueWithZero() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("number", 0);

        Integer result = ParameterValueConverter.getIntValue(dataMap, "Number");
        assertEquals(0, result);
    }

    @Test
    void testMultipleCallsWithSameMap() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("mode", "Adequacy");
        dataMap.put("number-of-mc-year", "100");
        dataMap.put("leap-year", "true");

        String stringResult = ParameterValueConverter.getStringValue(dataMap, "Mode");
        Integer intResult = ParameterValueConverter.getIntValue(dataMap, "Number of MC year");
        Boolean boolResult = ParameterValueConverter.getBooleanValue(dataMap, "Leap Year");

        assertEquals("Adequacy", stringResult);
        assertEquals(100, intResult);
        assertTrue(boolResult);
    }
}
