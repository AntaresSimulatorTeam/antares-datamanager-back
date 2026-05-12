package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersEntity;

import java.util.List;
import java.util.Set;

public record HydroAllocationRowProcessingResult(
        List<HydroAllocationEntity> entities,
        StringBuilder checksum,
        List<String> fileAreas,
        Set<String> invalidCombos
) implements HydroTechnicalParametersRowProcessingResult {
    @Override
    public void addArea(String area) {
        fileAreas.add(area);
    }

    @Override
    public StringBuilder getChecksumBuilder() {
        return checksum;
    }

    public void addEntity(HydroAllocationEntity entity) {
        entities.add(entity);
    }

    public List<HydroAllocationEntity> getEntities() {
        return entities;
    }
}


