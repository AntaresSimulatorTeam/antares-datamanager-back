package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum HydroParameterColumns {
    NODE("node"),
    INTER_DAILY_BREAKDOWN("inter.daily.breakdown"),
    INTRA_DAILY_MODULATION("intra.daily.modulation"),
    INTER_MONTHLY_BREAKDOWN("inter.monthly.breakdown"),
    INITIALIZE_RESERVOIR_DATE("initialize.reservoir.date"),
    PUMPING_EFFICIENCY("pumping.efficiency"),
    RESERVOIR("reservoir"),
    RESERVOIR_CAPACITY("reservoir.capacity"),
    FOLLOW_LOAD("follow.load"),
    USE_WATER("use.water");

    private final String displayName;

    HydroParameterColumns(String displayName) {
        this.displayName = displayName;
    }

    public static String[] getAllColumnNames() {
        return Arrays.stream(values())
                .map(HydroParameterColumns::getDisplayName)
                .toArray(String[]::new);
    }

    public static String[] getIntegerColumnNames() {
        return Arrays.stream(values())
                .map(HydroParameterColumns::getDisplayName)
                .filter(name -> name.equals("initialize.reservoir.date"))
                .toArray(String[]::new);
    }

    public static String[] getNumericColumnNames() {
        return Arrays.stream(values())
                .map(HydroParameterColumns::getDisplayName)
                .filter(name -> name.equals("inter.daily.breakdown")
                        || name.equals("intra.daily.modulation")
                        || name.equals("inter.monthly.breakdown")
                        || name.equals("pumping.efficiency")
                        || name.equals("reservoir.capacity"))
                .toArray(String[]::new);
    }

    public static String[] getBooleanColumnNames() {
        return Arrays.stream(values())
                .map(HydroParameterColumns::getDisplayName)
                .filter(name -> name.equals("reservoir")
                        || name.equals("follow.load")
                        || name.equals("use.water"))
                .toArray(String[]::new);
    }
}
