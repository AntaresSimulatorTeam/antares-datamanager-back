package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public enum ThermalEconomicCo2Columns {
    FUEL(0, "fuel"),
    COUNTRY(1, "country"),
    YEAR(2, "year"),
    CO2_EMISSION_FUEL(3, "CO2_EmissionFuel"),
    UNIT_CO2(4, "unitCO2"),
    COMMENT(5, "comment");

    private final int index;
    private final String displayName;

    public static List<String> getAllColumnNames() {
        return Arrays.stream(values())
                .map(ThermalEconomicCo2Columns::getDisplayName)
                .toList();
    }
}