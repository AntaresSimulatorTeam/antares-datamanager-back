package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum DsrClusterColumns {
    TO_USE("toUse"),
    AREA("Area"),
    NAME("Name"),
    CAPACITY("Capacity"),
    NB_HOUR_PER_DAY("nb_hour_per_day"),
    MAX_HOUR_PER_DAY("max_hour_per_day"),
    PRICE("price"),
    NB_UNITS("nb_units"),
    FO_RATE("FO_rate"),
    FO_DURATION("FO_duration"),
    MODULATION("Modulation");

    private final String displayName;

    DsrClusterColumns(String displayName) {
        this.displayName = displayName;
    }

    public static String[] getAllColumnNames() {
        return Arrays.stream(values())
                .map(DsrClusterColumns::getDisplayName)
                .toArray(String[]::new);
    }
}