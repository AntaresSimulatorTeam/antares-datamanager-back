package com.rte_france.antares.datamanager_back.util.excel_file_validators.columns_enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ConstraintMeColumns {
    NAME(0),
    ENABLED(1),
    SIGN(2),
    TEMPORALITY(3),
    TYPE(4),
    COMMENTS(5),
    NOEUD1_GAUCHE(6),
    NOEUD2_GAUCHE(7),
    CLUSTER_GAUCHE(8),
    NOEUD1_DROITE(9),
    NOEUD2_DROITE(10),
    CLUSTER_DROITE(11);

    private final int index;
}
