package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.repository.model.HydroParametersEntity;

import java.util.List;

public record HydroParametersRowProcessingResult(
        List<HydroParametersEntity> entities,
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

    public void addEntity(HydroParametersEntity entity) {
        entities.add(entity);
    }

    public List<HydroParametersEntity> getEntities() {
        return entities;
    }
}