package com.rte_france.antares.datamanager_back.service.settings;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class ParameterValueConverter {

    private static final String DEFAULT_STRING_VALUE = "";
    private static final Integer DEFAULT_INT_VALUE = 0;
    private static final Boolean DEFAULT_BOOLEAN_VALUE = false;

    private ParameterValueConverter() {
        // Utility class
    }

    public static String getStringValue(Map<String, Object> dataMap, String key) {
        Object value = dataMap.get(key.toLowerCase().replaceAll("\\s+", "-"));
        if (value == null) {
            return DEFAULT_STRING_VALUE;
        }
        try {
            String result = value.toString().trim();
            if ("none".equalsIgnoreCase(result)) {
                return "None";
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not convert value to string for key '{}': {}", key, value);
            return DEFAULT_STRING_VALUE;
        }
    }

    public static Integer getIntValue(Map<String, Object> dataMap, String key) {
        Object value = dataMap.get(key.toLowerCase().replaceAll("\\s+", "-"));
        if (value == null) {
            return DEFAULT_INT_VALUE;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            log.warn("Could not convert value to integer for key '{}': {}. Using default value: {}", key, value, DEFAULT_INT_VALUE);
            return DEFAULT_INT_VALUE;
        }
    }

    public static Boolean getBooleanValue(Map<String, Object> dataMap, String key) {
        Object value = dataMap.get(key.toLowerCase().replaceAll("\\s+", "-"));
        if (value == null) {
            return DEFAULT_BOOLEAN_VALUE;
        }
        try {
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            String strValue = value.toString().trim().toLowerCase();
            return "true".equals(strValue) || "yes".equals(strValue) || "1".equals(strValue);
        } catch (Exception e) {
            log.warn("Could not convert value to boolean for key '{}': {}. Using default value: {}", key, value, DEFAULT_BOOLEAN_VALUE);
            return DEFAULT_BOOLEAN_VALUE;
        }
    }
}
