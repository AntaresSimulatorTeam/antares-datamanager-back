package com.rte_france.antares.datamanager_back.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AreasGenerationContextDTO {
    private Map<String, List<String>> arrowLoadFilesByArea;
    private Map<String, Map<String, ThermalClusterGenerationDto>> clusterPropsByArea;
    private Map<String, StsGenerationDTO> stsClusterProps;
    private Map<String, DsrGenerationDTO> dsrClusterProps;
    private Map<String, List<MiscGenerationDTO>> miscProps;
    private Map<String, Map<String, ResClusterGenerationDto>> resProps;
    private Map<String, HydroAreaGenerationDTO> hydroProps;
}
