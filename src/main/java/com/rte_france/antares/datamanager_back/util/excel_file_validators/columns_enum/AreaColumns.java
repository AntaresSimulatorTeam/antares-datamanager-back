package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

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

    public static List<String> getBooleanColumnNames() {
        return Arrays.stream(values())
                .map(AreaColumns::getDisplayName)
                .filter(name -> name.equals("Power To Gas") || name.equals("Stockage court terme"))
                .toList();
    }

    public static List<String> getStringColumnNames() {
        return Arrays.stream(values())
                .map(AreaColumns::getDisplayName)
                .filter(name -> name.equals("areas"))
                .toList();
    }

    public static List<String> getNumericalColumnNames() {
        return Arrays.stream(values())
                .map(AreaColumns::getDisplayName)
                .filter(name -> name.equals("x") || name.equals("y") || name.equals("r") || name.equals("g") || name.equals("b"))
                .toList();
    }

}
