package com.rte_france.antares.datamanager_back.util.columnsEnums;

import lombok.Getter;

@Getter
public enum AreaColumns {
    AREAS("areas"),
    POWER_TO_GAS("Power To Gas"),
    STOCKAGE_COURT_TERME("Stockage court terme"),
    X("x"),
    Y("y"),
    R("r"),
    G("g"),
    B("b");


    private final String displayName;

    AreaColumns(String displayName) {
        this.displayName = displayName;
    }

}
