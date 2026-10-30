package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HydroParametersMeColumns {
    NODE(0, "node"),
    INTER_MONTHLY_CORRELATION(1, "inter.monthly.correlation"),
    INTER_DAILY_BREAKDOWN(2, "inter.daily.breakdown"),
    INTER_DAILY_MODULATION(3, "inter.daily.modulation"),
    INTER_MONTHLY_BREAKDOWN(4, "inter.monthly.breakdown"),
    INITIALIZE_RESERVOIR_DATE(5, "initialize.reservoir.date"),
    LEEWAY_LOW(6, "leeway.low"),
    LEEWAY_UP(7, "leeway.up"),
    PUMPING_EFFICIENCY(8, "pumping.efficiency"),
    RESERVOIR_MANAGEMENT(9, "reservoir management"),
    FOLLOW_LOAD(10, "follow.load"),
    USE_HEURISTIC(11, "use.heuristic"),
    USE_WATER(12, "use.water"),
    HARD_BOUNDS(13, "hard.bounds"),
    USE_LEEWAY(14, "use.leeway"),
    POWER_TO_LEVEL(15, "power.to.level");

    private final int index;
    private final String displayName;
}
