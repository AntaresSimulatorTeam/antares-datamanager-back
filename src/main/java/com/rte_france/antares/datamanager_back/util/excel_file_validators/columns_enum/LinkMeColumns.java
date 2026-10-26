package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LinkMeColumns {
    NODE_FROM(0),
    NODE_TO(1),
    DIRECT_MW(2),
    INDIRECT_MW(3),
    HURDLE_COSTS_DIRECT(4),
    HURDLE_COSTS_INDIRECT(5);

    private final int index;
}
