package com.rte_france.antares.datamanager_back.service.res;

import com.rte_france.antares.datamanager_back.repository.model.ResClusterCapacityEntity;

import java.util.List;
import java.util.Set;

public record ResRowProcessingCapacityResult(
        List<ResClusterCapacityEntity> entities,
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

    @Override
    public StringBuilder getChecksumBuilder() {
        return checksum;
    }

    public void addEntity(ResClusterCapacityEntity entity) {
        entities.add(entity);
    }
}


