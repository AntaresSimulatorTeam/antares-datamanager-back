package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public enum P2gColumns {
    P2G_FATALBAND("P2G_fatalband",3, ColumnType.NUMERIC),
    P2G_ASSERVI("P2G_asservi",4, ColumnType.NUMERIC),
    P2G_METHANATION("P2G_methanation",6, ColumnType.NUMERIC),
    P2G_BASE_EFF("P2G_base_eff",7, ColumnType.NUMERIC),
    TO_LINK_P2G_MARG("To_Links_p2G_marg",9, ColumnType.NUMERIC),
    TO_LINK_P2G_BASE("To_Links_p2G_base (P2G base + fatal)",10, ColumnType.NUMERIC),
    AREA_ANTARES_NAME("area_antares_name",2, ColumnType.STRING),
    TYPE_P2G("type P2G", 0, ColumnType.REQUIRED_COSTS),
    MODULATION("modulation",2, ColumnType.REQUIRED_COSTS);

    private final String displayName;
    private final int value;
    private final ColumnType type;

    P2gColumns(String displayName, int value, ColumnType type) {
        this.displayName = displayName;
        this.value = value;
        this.type = type;
    }

    public enum ColumnType {
        NUMERIC, STRING, REQUIRED_COSTS
    }

    public static Map<String, Integer> getAllNumericColumnNames(){
        return Arrays.stream(values())
                .filter(v -> v.type.equals(ColumnType.NUMERIC))
                .collect(Collectors.toMap(P2gColumns::getDisplayName, P2gColumns::getValue, (v1, v2) -> v1, LinkedHashMap::new));
    }

    public static Map<String, Integer> getAllStringColumnNames(){
        return Arrays.stream(values())
                .filter(v -> v.type.equals(ColumnType.STRING))
                .collect(Collectors.toMap(P2gColumns::getDisplayName, P2gColumns::getValue));
    }

    public static Map<String, Integer> getAllRequiredCostsColumnNames(){
        return Arrays.stream(values())
                .filter(v -> v.type.equals(ColumnType.REQUIRED_COSTS))
                .collect(Collectors.toMap(P2gColumns::getDisplayName, P2gColumns::getValue));
    }
}

