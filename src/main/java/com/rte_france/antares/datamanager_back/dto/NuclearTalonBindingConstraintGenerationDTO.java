package com.rte_france.antares.datamanager_back.dto;

import java.util.List;

public record NuclearTalonBindingConstraintGenerationDTO(
        String group,
        int nbTsColumns,
        List<String> frStandardClusters,
        String series
) {}
