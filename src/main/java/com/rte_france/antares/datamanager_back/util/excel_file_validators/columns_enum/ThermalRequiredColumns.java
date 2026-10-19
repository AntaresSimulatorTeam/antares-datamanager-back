package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum ThermalRequiredColumns {
    TO_USE("ToUse"),
    AREA("Area"),
    FUEL("Fuel"),
    TECHNOLOGY("Technology"),
    CLUSTER("Cluster"),
    CATEGORY("Category");

    private final String displayName;

    ThermalRequiredColumns(String displayName) {
        this.displayName = displayName;
    }

    public static List<String> getAllColumnNames() {
        return Arrays.stream(values())
                .map(ThermalRequiredColumns::getDisplayName)
                .toList();
    }
}
