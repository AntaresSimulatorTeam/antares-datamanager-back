package com.rte_france.antares.datamanager_back.repository.model;

import java.util.Locale;

public enum ResGroupEnum {

    WIND_ONSHORE("wind_onshore"),
    WIND_OFFSHORE("wind_offshore"),
    SOLAR_PV("solar_pv"),
    SOLAR_THERMO("solar_thermo");

    private final String value;

    ResGroupEnum(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static String normalizeForGenerator(String group) {
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException("RES group is missing");
        }
        String normalized = group.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replaceAll("\\s+", "_");
        String compact = normalized.replace("_", "");

        for (ResGroupEnum resGroup : values()) {
            if (resGroup.value.equals(normalized)
                    || resGroup.value.replace("_", "").equals(compact)) {
                return resGroup.value;
            }
        }

        throw new IllegalArgumentException("Unsupported RES group: " + group);
    }
}

