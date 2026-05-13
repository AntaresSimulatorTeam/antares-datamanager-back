package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
    HVDC_NB_DIRECT("HVDC_nb_direct"),
    HVDC_NB_INDIRECT("HVDC_nb_indirect"),
    HVDC_FO_RATE_DIRECT("HVDC_FO_Rate_direct"),
    HVDC_FO_RATE_INDIRECT("HVDC_FO_Rate_indirect");

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
                .filter(name -> {
                    String lowerName = name.toLowerCase(Locale.ROOT);
                    return lowerName.startsWith("summer") || lowerName.startsWith("winter")
                            || lowerName.startsWith("hvdc_mw") || lowerName.startsWith("hvdc_nb") || lowerName.startsWith("hvdc_fo_rate");
                })
                .toList();
    }

    public static List<String> getBooleanColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getDisplayName)
                .filter(name -> name.equalsIgnoreCase("Flowbased_perimeter"))
                .toList();
    }

    public static List<String> getDirectColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getDisplayName)
                .filter(name -> {
                    String lowerName = name.toLowerCase(Locale.ROOT);
                    return lowerName.contains("direct") && !lowerName.contains("indirect");
                })
                .toList();
    }

    public static List<String> getIndirectColumnNames() {
        return Arrays.stream(values())
                .map(LinksColumns::getDisplayName)
                .filter(name -> name.toLowerCase(Locale.ROOT).contains("indirect"))
                .toList();
    }

}
