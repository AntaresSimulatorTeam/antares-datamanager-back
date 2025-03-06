package com.rte_france.antares.datamanager_back.util.ExcelFileValidators.ColumnsEnums;

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
    SUMMER_HP_INDIRECT("Summer_HP_Indirect_MW"),
    SUMMER_HC_DIRECT("Summer_HC_Direct_MW"),
    SUMMER_HC_INDIRECT("Summer_HC_Indirect_MW"),
    FLOWBASED_PERIMETER("Flowbased_perimeter"),
    HVDC("HVDC"),
    SPECIFIC_TS("Specific_TS"),
    FORCED_OUTAGE_HVAC("Forced_Outage_HVAC");

    private final String displayName;

    LinksColumns(String displayName) {
        this.displayName = displayName;
    }

    public static List<String> getAllColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getDisplayName)
                .toList();
    }

    public static List<String> getNumericColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getDisplayName)
                .filter(name -> name.startsWith("Summer") || name.startsWith("Winter"))
                .toList();
    }

    public static List<String> getBooleanColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getDisplayName)
                .filter(name -> name.equals("Flowbased_perimeter") || name.equals("HVDC")
                        || name.equals("Forced_Outage_HVAC") || name.equals("Specific_TS"))
                .toList();
    }

    public static List<String> getDirectColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getDisplayName)
                .filter(name -> name.contains("Direct"))
                .toList();
    }

    public static List<String> getIndirectColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getDisplayName)
                .filter(name -> name.contains("Indirect"))
                .toList();
    }

}
