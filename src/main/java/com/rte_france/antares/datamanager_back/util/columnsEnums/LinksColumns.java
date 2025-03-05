package com.rte_france.antares.datamanager_back.util.columnsEnums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum LinksColumns {
    NAME("Name"),
    WINTER_HP_DIRECT("Winter_HP_Direct_MW"),
    WINTER_HP_INDIRECT("Winter_HP_Indirect_MW"),
    WINTER_HC_DIRECT("Winter_HC_Direct_MW"),
    WINTER_HC_INDIRECT("Winter_HC_Indirect_MW"),
    SUMMER_HP_DIRECT("Summer_HP_Direct_MW"),
    SUMMER_HP_INDIRECT("Summer_HP_Direct_MW"),
    SUMMER_HC_DIRECT("Summer_HC_Direct_MW"),
    SUMMER_HC_INDIRECT("Summer_HC_Indirect_MW"),
    FLOWBASED_PERIMETER("Flowbased_perimeter"),
    HVDC("HVDC"),
    SPECIFIC_TS("Specific_TS"),
    FORCED_OUTAGE_HVAC("Forced_Outage_HVAC");

    private final String columnName;

    LinksColumns(String columnName) {
        this.columnName = columnName;
    }

    public static List<String> getAllColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getColumnName)
                .toList();
    }

    public static List<String> getNumericColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getColumnName)
                .filter(name -> name.startsWith("Summer") || name.startsWith("Winter"))
                .toList();
    }
}
