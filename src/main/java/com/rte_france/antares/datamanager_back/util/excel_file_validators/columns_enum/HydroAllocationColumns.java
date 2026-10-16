package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum HydroAllocationColumns {
    HYDRO("hydro"),
    LOAD("load"),
    ALLOCATION("allocation");

    private final String displayName;

    HydroAllocationColumns(String displayName) {
        this.displayName = displayName;
    }

    public static String[] getAllColumnNames() {
        return Arrays.stream(values())
                .map(HydroAllocationColumns::getDisplayName)
                .toArray(String[]::new);
    }
}
