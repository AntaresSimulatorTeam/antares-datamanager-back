package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ThermalEconomicCostColumns {
    COUNTRY(0, "country"),
    FUEL(1, "fuel"),
    COMMENT(2, "comment"),
    UNIT(3, "unit"),
    MODULATION(4, "modulation"),
    RATIO(5, "ratio_ncv_hcv");

    private final int index;
    private final String displayName;

    public static List<String> getAllColumnNames() {
        return Arrays.stream(values())
                .map(ThermalEconomicCostColumns::getDisplayName)
                .toList();
    }
}
