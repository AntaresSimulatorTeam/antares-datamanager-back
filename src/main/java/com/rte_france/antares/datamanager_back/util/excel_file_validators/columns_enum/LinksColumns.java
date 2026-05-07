package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

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
    HVDC_MW_DIRECT("HVDC_MW_Direct"),
    HVDC_MW_INDIRECT("HVDC_MW_Indirect"),
    HVDC_NB("HVDC_nb"),
    HVDC_FO_RATE("HVDC_FO_Rate");

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
                .filter(name -> name.startsWith("Summer") || name.startsWith("Winter")
                        || name.startsWith("HVDC_MW") || name.equals("HVDC_nb") || name.equals("HVDC_FO_Rate"))
                .toList();
    }

    public static List<String> getBooleanColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getDisplayName)
                .filter(name -> name.equals("Flowbased_perimeter"))
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
