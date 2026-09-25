package com.rte_france.antares.datamanager_back.service.multi_energy;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService.AreaClusterRefKey;

import java.util.Collections;
import java.util.Map;

public interface MultiEnergyService {

    /**
     * Builds the Multi-Energy (ME) JSON data map for the given ME trajectories.
     *
     * @param study the study entity
     * @param thermalClusterProps thermal cluster properties (e.g. efficiency) keyed by area/cluster,
     *                             used to enrich G2P binding constraints
     * @param trajectories the ME trajectories to process
     * @return the ME data map
     */
    Map<String, Object> buildMultiEnergyMap(
            StudyEntity study,
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps,
            TrajectoryEntity... trajectories);

    /**
     * Builds the Multi-Energy (ME) JSON data map for the given ME trajectories, without any
     * thermal cluster properties (G2P constraint clusters will be omitted).
     *
     * @param study the study entity
     * @param trajectories the ME trajectories to process
     * @return the ME data map
     */
    default Map<String, Object> buildMultiEnergyMap(
            StudyEntity study,
            TrajectoryEntity... trajectories) {
        return buildMultiEnergyMap(study, Collections.emptyMap(), trajectories);
    }
}
