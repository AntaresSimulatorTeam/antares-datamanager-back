package com.rte_france.antares.datamanager_back.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConstraintRowData {
    private boolean enabled;
    private String sign;
    private String temporality;
    private String type;
    private String comments;
    private String noeud1Gauche;
    private String noeud2Gauche;
    private String clusterGauche;
    private String noeud1Droite;
    private String noeud2Droite;
    private String clusterDroite;
}
