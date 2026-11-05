package com.rte_france.antares.datamanager_back.service.p2g;

import com.rte_france.antares.datamanager_back.dto.P2gGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

public interface P2gGenerationAssemblerService {

    /**
     * Builds P2G clusters using only capacities whose area belongs to the study's AREA trajectory.
     * Zero capacities are retained; null capacities keep the existing cluster and link omission rules.
     */
    P2gGenerationDTO assembleP2g(StudyEntity studyEntity, TrajectoryEntity capacityCostTrajectory, TrajectoryEntity marketModulationTrajectory);
}
