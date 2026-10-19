package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum StStorageColumns {
    AREA("Area"),
    NAME("Name"),
    GROUP("Group"),
    INJECTION("Injection"),
    WITHDRAWAL("Withdrawal"),
    STORAGE("Storage"),
    EFFICIENCY_INJECTION("Efficiency_injection"),
    EFFICIENCY_WITHDRAWAL("Efficiency_withdrawal"),
    INITIAL_LEVEL("Initial_level"),
    INITIAL_LEVEL_OPTIM("Initial_level_optim"),
    ENABLED("Enabled"),
    SERIES("Series"),
    CONSTRAINTS("Constraints");

    private final String displayName;

    StStorageColumns(String displayName) {
        this.displayName = displayName;
    }

    public static String[] getAllColumnNames() {
        return Arrays.stream(values())
                .map(StStorageColumns::getDisplayName)
                .toArray(String[]::new);
    }
}