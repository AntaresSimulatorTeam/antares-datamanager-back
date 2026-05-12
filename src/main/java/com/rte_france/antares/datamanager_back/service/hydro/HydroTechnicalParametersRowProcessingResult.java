package com.rte_france.antares.datamanager_back.service.hydro;

import java.util.List;

public sealed interface HydroTechnicalParametersRowProcessingResult permits HydroAllocationRowProcessingResult, HydroParametersRowProcessingResult {
    StringBuilder checksum();
    List<String> fileAreas();

    void addArea(String area);
    StringBuilder getChecksumBuilder();
}