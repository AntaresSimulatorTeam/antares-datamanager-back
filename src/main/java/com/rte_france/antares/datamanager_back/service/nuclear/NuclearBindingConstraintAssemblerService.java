package com.rte_france.antares.datamanager_back.service.nuclear;

import com.rte_france.antares.datamanager_back.dto.NuclearBindingConstraintGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.NuclearTalonBindingConstraintGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.util.List;

public interface NuclearBindingConstraintAssemblerService {
    NuclearBindingConstraintGenerationDTO assembleModulationBindingConstraints(StudyEntity studyEntity, TrajectoryEntity modulationTrajectory, List<String> frNuclearClusterNames);

    NuclearTalonBindingConstraintGenerationDTO assembleTalonBindingConstraint(StudyEntity studyEntity, TrajectoryEntity talonTrajectory, List<String> frNuclearClusterNames);
}