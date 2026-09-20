package com.rte_france.antares.datamanager_back.service.multi_energy;

import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.util.Map;

public interface MultiEnergyService {

    /**
     * Builds the Multi-Energy (ME) JSON data map for the given ME trajectories.
     *
     * @param study the study entity
     * @param trajectories the ME trajectories to process
     * @return the ME data map
     */
    Map<String, Object> buildMultiEnergyMap(
            StudyEntity study,
            TrajectoryEntity... trajectories);
}
