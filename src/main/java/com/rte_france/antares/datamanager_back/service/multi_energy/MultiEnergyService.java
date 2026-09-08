package com.rte_france.antares.datamanager_back.service.multi_energy;

import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.util.Map;

public interface MultiEnergyService {

    /**
     * Builds the Multi-Energy (ME) JSON data map for an AREA_ME or LINK_ME trajectory in a study.
     *
     * @param study the study entity
     * @param areaMeTrajectory the AREA_ME or LINK_ME trajectory entity
     * @return the ME data map
     */
    Map<String, Object> buildMultiEnergyMap(StudyEntity study, TrajectoryEntity areaMeTrajectory);

    /**
     * Builds the Multi-Energy (ME) JSON data map for AREA_ME and LINK_ME trajectories in a study.
     *
     * @param study the study entity
     * @param areaMeTrajectory the AREA_ME trajectory entity (optional, can be null)
     * @param linkMeTrajectory the LINK_ME trajectory entity (optional, can be null)
     * @return the ME data map
     */
    Map<String, Object> buildMultiEnergyMap(StudyEntity study, TrajectoryEntity areaMeTrajectory, TrajectoryEntity linkMeTrajectory);

}
