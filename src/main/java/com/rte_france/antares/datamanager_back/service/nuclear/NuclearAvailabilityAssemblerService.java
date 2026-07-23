package com.rte_france.antares.datamanager_back.service.nuclear;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService.AreaClusterRefKey;

import java.util.Map;

public interface NuclearAvailabilityAssemblerService {

    /**
     * Computes the nuclear availability series (LT, EPR, SMR) for whichever of the three
     * trajectories are linked to the study. LT and EPR clusters get a ready Arrow file and SMR clusters get
     * a shared Arrow file and the metadata (active unit count, seed) needed for the
     * python generator to perform the seeded draw itself. 
     *  
     * @return an empty safe result when none
     * of the three trajectories are linked.
     */
    NuclearAvailabilityAssemblyResult assembleAvailability(
            StudyEntity study,
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps);
}
