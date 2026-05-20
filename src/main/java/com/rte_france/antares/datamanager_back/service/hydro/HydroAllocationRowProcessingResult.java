package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationEntity;

import java.util.List;

public record HydroAllocationRowProcessingResult(
        List<HydroAllocationEntity> entities,
        StringBuilder checksum,
        List<String> fileAreas
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


