package com.rte_france.antares.datamanager_back.service.p2g;

import com.rte_france.antares.datamanager_back.dto.P2gGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

public interface P2gGenerationAssemblerService {

    P2gGenerationDTO assembleP2g(StudyEntity studyEntity, TrajectoryEntity capacityCostTrajectory, TrajectoryEntity marketModulationTrajectory);
}
