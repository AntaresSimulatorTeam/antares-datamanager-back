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
    public List<ResClusterCapacityEntity> getEntities() {
        return entities;
    }

    @Override
    public StringBuilder getChecksum() {
        return checksum;
    }

    @Override
    public List<String> getFileAreas() {
        return fileAreas;
    }

    @Override
    public List<String> getFileTechnologies() {
        return fileTechnologies;
    }

    @Override
    public Set<String> getInvalidCombos() {
        return invalidCombos;
    }

    @Override
    public void addEntity(Object entity) {
        entities.add((ResClusterCapacityEntity) entity);
    }

    @Override
    public void addArea(String area) {
        fileAreas.add(area);
    }

    @Override
    public void addTechnologies(String technology) {
        fileTechnologies.add(technology);
    }

    @Override
    public ResRowProcessingResult merge(ResRowProcessingResult other) {
        if (other instanceof ResRowProcessingCapacityResult(
                List<ResClusterCapacityEntity> entities1, StringBuilder checksum1, List<String> areas,
                List<String> technologies, Set<String> combos
        )) {
            entities.addAll(entities1);
            checksum.append(checksum1);
            fileAreas.addAll(areas);
            fileTechnologies.addAll(technologies);
            invalidCombos.addAll(combos);
            return this;
        }
        throw new IllegalArgumentException("Cannot merge different result types");
    }
}


