package com.rte_france.antares.datamanager_back.service.res;

import java.util.List;
import java.util.Set;

public sealed interface ResRowProcessingResult permits ResRowProcessingCapacityResult, ResRowProcessingTechnologyDistributionResult {

    List<?> entities();
    StringBuilder checksum();
    List<String> fileAreas();
    List<String> fileTechnologies();
    Set<String> invalidCombos();

    List<?> getEntities();
    StringBuilder getChecksum();
    List<String> getFileAreas();
    List<String> getFileTechnologies();
    Set<String> getInvalidCombos();

    void addEntity(Object entity);
    void addArea(String area);
    void addTechnologies(String technologies);
    ResRowProcessingResult merge(ResRowProcessingResult other);
}

