package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum ThermalClusterColumns {
    TO_USE("ToUse"),
    AREA("Area"),
    FUEL("Fuel"),
    TECHNOLOGY("Technology"),
    CLUSTER("Cluster"),
    CATEGORY("Category");

    private final String displayName;

    ThermalClusterColumns(String displayName) {
        this.displayName = displayName;
    }

    public static List<String> getAllColumnNames() {
        return Arrays.stream(values())
                .map(ThermalClusterColumns::getDisplayName)
                .toList();
    }
}
