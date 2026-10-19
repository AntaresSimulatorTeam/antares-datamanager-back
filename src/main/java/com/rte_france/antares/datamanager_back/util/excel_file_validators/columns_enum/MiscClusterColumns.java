package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum MiscClusterColumns {
    TO_USE("toUse"),
    AREA("Area"),
    GROUP("Group"),
    CLUSTER("Cluster"),
    CATEGORY("Category");

    private final String displayName;


    MiscClusterColumns(String displayName) {
        this.displayName = displayName;
    }

    public static String[] getAllColumnNames() {
        return Arrays.stream(values())
                .map(MiscClusterColumns::getDisplayName)
                .toArray(String[]::new);
    }
}
