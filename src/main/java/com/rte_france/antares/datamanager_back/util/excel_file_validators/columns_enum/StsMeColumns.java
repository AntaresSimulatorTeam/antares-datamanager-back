package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StsMeColumns {
    NODE(0),
    NAME(1),
    GROUP(2),
    INJECTION(3),
    WITHDRAWAL(4),
    STORAGE(5),
    EFFICIENCY(6),
    INITIAL_LEVEL(7),
    INITIAL_LEVEL_OPTIM(8),
    ENABLED(9),
    SERIES(10),
    CONSTRAINTS_FLAG(11);

    private final int index;
}
