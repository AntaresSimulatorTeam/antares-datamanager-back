package com.rte_france.antares.datamanager_back.service.res;

import com.rte_france.antares.datamanager_back.repository.model.ResTechnologyDistributionEntity;

import java.util.List;
import java.util.Set;

public record ResRowProcessingTechnologyDistributionResult(
        List<ResTechnologyDistributionEntity> entities,
        StringBuilder checksum,
        List<String> fileAreas,
        List<String> fileTechnologies,
        Set<String> invalidCombos
) implements ResRowProcessingResult {
    @Override
    public void addArea(String area) {
        fileAreas.add(area);
    }

    @Override
    public void addTechnologies(String technology) {
        fileTechnologies.add(technology);
    }

    public void addEntity(ResTechnologyDistributionEntity entity) {
        entities.add(entity);
    }
}


