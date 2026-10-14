package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.Getter;

@Getter
public enum FlowbasedConstraintsRestrictionsColumns {
    NOM("nom"),
    ENABLED("enabled"),
    SIGNE("signe"),
    TEMPORALITY("temporalité"),
    TYPE("type"),
    INFORMATION("information forme de la contrainte"),
    LIST_LINKS("listLinks"),
    SECOND_MEMBER("second_member");

    private final String displayName;

    FlowbasedConstraintsRestrictionsColumns(String displayName) {
        this.displayName = displayName;
    }
}
