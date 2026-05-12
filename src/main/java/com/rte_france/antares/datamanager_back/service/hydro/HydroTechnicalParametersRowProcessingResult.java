package com.rte_france.antares.datamanager_back.service.hydro;

import java.util.List;
import java.util.Set;

public sealed interface HydroTechnicalParametersRowProcessingResult permits HydroAllocationRowProcessingResult, HydroParametersRowProcessingResult {
    StringBuilder checksum();
    List<String> fileAreas();
    Set<String> invalidCombos();
    
    void addArea(String area);
    StringBuilder getChecksumBuilder();
}

