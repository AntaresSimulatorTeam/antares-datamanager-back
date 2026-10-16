package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum ThermalCommonParamColumns {
    CLUSTER_PEMMDB("cluster_PEMMDB"),
    CLUSTER_BP("cluster_BP"),
    CATEGORY("Category"),
    FUEL("Fuel"),
    TYPE("Type"),
    EFFICIENCY_RANGE("efficiency_range"),
    EFFICIENCY_DEFAULT("efficiency_default"),
    CO2("CO2"),
    OM_COST("OM_cost"),
    MIN_UP_TIME("min_up_time"),
    MIN_DOWN_TIME("min_down_time"),
    START_UP_FUEL("start_up_fuel"),
    START_UP_FIX_COST("start_up_fix_cost"),
    START_UP_FUEL_COLD_START("start_up_fuel_cold_start"),
    START_UP_FIX_COST_COLD_START("start_up_fix_cost_cold_start"),
    START_UP_FUEL_HOT_START("start_up_fuel_hot_start"),
    START_UP_FIX_COST_HOT_START("start_up_fix_cost_hot_start"),
    TRANSITION_HOT_WARM("transition_hot_warm"),
    TRANSITION_HOT_COLD("transition_hot_cold"),
    SHUTDOWN_TIME("shutdown_time"),
    STARTUP_TIME("startup_time"),
    FO_RATE_DEFAULT("FO_rate_default"),
    FO_DURATION_DEFAULT("FO_duration_default"),
    PO_DURATION_DEFAULT("PO_duration_default"),
    PO_WINTER_DEFAULT("PO_winter_default"),
    MIN_STABLE_GENERATION_DEFAULT("min_stable_generation_default"),
    RAMP_UP("ramp_up"),
    RAMP_DOWN("ramp_down"),
    FIXED_GENERATION_REDUCTION("fixed_generation_reduction");

    private final String displayName;

    ThermalCommonParamColumns(String displayName) {
        this.displayName = displayName;
    }

    public static List<String> getAllColumnNames() {
        return Arrays.stream(values())
                .map(ThermalCommonParamColumns::getDisplayName)
                .toList();
    }
}
