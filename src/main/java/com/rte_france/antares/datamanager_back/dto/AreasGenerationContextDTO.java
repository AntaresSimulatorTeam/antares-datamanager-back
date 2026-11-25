package com.rte_france.antares.datamanager_back.dto;

import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaKey;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AreasGenerationContextDTO {
    private Map<AreaKey, List<String>> arrowLoadFilesByArea;
    private Map<AreaKey, Map<String, ThermalClusterGenerationDto>> clusterPropsByArea;
    private Map<String, StsGenerationDTO> stsClusterProps;
    private Map<String, DsrGenerationDTO> dsrClusterProps;
    private Map<AreaKey, List<MiscGenerationDTO>> miscProps;
    private Map<AreaKey, Map<String, ResClusterGenerationDto>> resProps;
    private Map<AreaKey, HydroAreaGenerationDTO> hydroProps;
    private Map<String, String> nuclearSeriesByClusterKey;
    private Map<String, NuclearSMRMixageDTO> nuclearSmrMixageByClusterKey;
}
