package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum AreaColumns {
    AREAS("areas"),
    DISTRICT("district"),
    SPILLED_ENERGY_COST("spilled energy cost"),
    UNSUPPLIED_ENERGY_COST("unsupplied energy cost"),
    X("x"),
    Y("y"),
    R("r"),
    G("g"),
    B("b");


    private final String displayName;

    AreaColumns(String displayName) {
        this.displayName = displayName;
    }



    public static List<String> getStringColumnNames() {
        return Arrays.stream(values())
                .map(AreaColumns::getDisplayName)
                .filter(name -> name.equals("areas") || name.equals("district"))
                .toList();
    }

    public static List<String> getNumericalColumnNames() {
        return Arrays.stream(values())
                .map(AreaColumns::getDisplayName)
                .filter(name -> name.equals("x")
                        || name.equals("y")
                        || name.equals("r")
                        || name.equals("g")
                        || name.equals("b")
                        || name.equals("spilled energy cost")
                        || name.equals("unsupplied energy cost"))
                .toList();
    }

}
