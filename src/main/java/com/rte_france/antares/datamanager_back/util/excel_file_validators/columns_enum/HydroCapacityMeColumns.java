package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HydroCapacityMeColumns {
    NODE(0, "Node"),
    RESERVOIR_CAPACITY(1, "Reservoir Capacity [MWh]"),
    GENERATING_PMAX_TIMESTEP(2, "Generating Pmax - timestep (daily/annual)"),
    GENERATING_PMAX(3, "Generating Pmax [MW]"),
    HOURS_AT_GENERATING_PMAX(4, "hours at generating Pmax"),
    PUMPING_PMAX_TIMESTEP(5, "Pumping Pmax - timestep (daily/annual)"),
    PUMPING_PMAX(6, "Pumping Pmax [MW]"),
    HOURS_AT_PUMPING_PMAX(7, "hours at pumping Pmax");

    private final int index;
    private final String displayName;
}
