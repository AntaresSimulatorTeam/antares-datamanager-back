package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ResColumns {
    GROUP("Group"),
    CLUSTER("Cluster"),
    AREA("Area"),
    PECD_ZONE("PECD_Zone"),
    TO_USE("ToUse"),
    CATEGORY("Category"),
    TECHNO_PECD("Techno_PECD");

    private final String displayName;

    ResColumns(String displayName) {
        this.displayName = displayName;
    }

    public static String[] getRequiredClusterColumnNames() {
        return Arrays.stream(values())
                .filter(col -> col.equals(ResColumns.TO_USE)
                        || col.equals(ResColumns.AREA)
                        || col.equals(ResColumns.GROUP)
                        || col.equals(ResColumns.CLUSTER)
                        || col.equals(ResColumns.CATEGORY))
                .map(ResColumns::getDisplayName)
                .toArray(String[]::new);
    }

    public static String[] getRequiredOffshoreClusterColumnNames() {
        return Arrays.stream(values())
                .filter(col -> col.equals(ResColumns.TO_USE)
                        || col.equals(ResColumns.AREA)
                        || col.equals(ResColumns.GROUP)
                        || col.equals(ResColumns.CLUSTER)
                        || col.equals(ResColumns.PECD_ZONE))
                .map(ResColumns::getDisplayName)
                .toArray(String[]::new);
    }

    public static String[] getRequiredTechnologyDistributionColumnNames() {
        return Arrays.stream(values())
                .filter(col -> col.equals(ResColumns.AREA)
                        || col.equals(ResColumns.GROUP)
                        || col.equals(ResColumns.CLUSTER)
                        || col.equals(ResColumns.TECHNO_PECD)
                        || col.equals(ResColumns.PECD_ZONE))
                .map(ResColumns::getDisplayName)
                .toArray(String[]::new);
    }

    public static String[] getRequiredZonalDistributionColumnNames() {
        return Arrays.stream(values())
                .filter(col -> col.equals(ResColumns.AREA)
                        || col.equals(ResColumns.GROUP)
                        || col.equals(ResColumns.PECD_ZONE))
                .map(ResColumns::getDisplayName)
                .toArray(String[]::new);
    }
}