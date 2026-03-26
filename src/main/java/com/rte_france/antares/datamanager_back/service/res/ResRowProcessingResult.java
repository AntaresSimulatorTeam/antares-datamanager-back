package com.rte_france.antares.datamanager_back.service.res;

import java.util.List;
import java.util.Set;

public sealed interface ResRowProcessingResult permits ResRowProcessingCapacityResult, ResRowProcessingTechnologyDistributionResult {
    StringBuilder checksum();
    List<String> fileAreas();
    List<String> fileTechnologies();
    Set<String> invalidCombos();
    
    void addArea(String area);
    void addTechnologies(String technologies);
}

