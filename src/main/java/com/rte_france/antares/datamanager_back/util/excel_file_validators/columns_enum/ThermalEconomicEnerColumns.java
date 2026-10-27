package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public enum ThermalEconomicEnerColumns {
    VALUE(0, "value"),
    UNIT(1, "unit"),
    COMMENT(2, "comment");

    private final int index;
    private final String displayName;

    public static List<String> getAllColumnNames() {
        return Arrays.stream(values())
                .map(ThermalEconomicEnerColumns::getDisplayName)
                .toList();
    }
}
