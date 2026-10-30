package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public enum AreaColumns {
    AREAS(0, "areas"),
    DISTRICT(1, "district"),
    SPILLED_ENERGY_COST(2, "spilled energy cost"),
    UNSUPPLIED_ENERGY_COST(3, "unsupplied energy cost"),
    X(4, "x"),
    Y(5, "y"),
    R(6, "r"),
    G(7, "g"),
    B(8, "b");

    private final int index;
    private final String displayName;

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
