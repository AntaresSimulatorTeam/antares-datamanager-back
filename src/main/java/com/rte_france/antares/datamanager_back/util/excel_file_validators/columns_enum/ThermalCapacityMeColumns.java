package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ThermalCapacityMeColumns {
    NODE(0),
    GROUP_NAME(1),
    CLUSTER_NAME(2),
    ENABLED(3),
    NOMINAL_CAPACITY(4),
    NB_UNIT(5),
    MARGINAL_COST(6),
    MARGINAL_COST_TIMESTEP(7),
    MARGINAL_COST_MODULATION(8),
    MARKET_BID_COST(9),
    MARKET_BID_COST_TIMESTEP(10),
    MARKET_BID_COST_MODULATION(11),
    MUST_RUN(12),
    MR_TIMESTEP(13),
    MR_MODULATION(14),
    CM_TIMESTEP(15),
    CM_MODULATION(16);

    private final int index;
}
