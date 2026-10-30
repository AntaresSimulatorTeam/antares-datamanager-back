package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EfficiencyMeColumns {
    NODE_CLUSTER(0),
    TYPE(1),
    COMMENTS(2),
    EFFICIENCY(3);

    private final int index;
}
