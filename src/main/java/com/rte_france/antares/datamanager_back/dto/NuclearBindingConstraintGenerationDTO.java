package com.rte_france.antares.datamanager_back.dto;

import java.util.List;

public record NuclearBindingConstraintGenerationDTO(
        String group,
        int nbTsColumns,
        List<String> frStandardClusters,
        List<String> frPeakClusters,
        List<String> yNucModulationClusters,
        List<NuclearConstraintItemDTO> constraints
) {}