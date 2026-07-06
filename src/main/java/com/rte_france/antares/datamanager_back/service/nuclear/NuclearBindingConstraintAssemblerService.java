package com.rte_france.antares.datamanager_back.service.nuclear;

import com.rte_france.antares.datamanager_back.dto.NuclearBindingConstraintGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.util.List;

public interface NuclearBindingConstraintAssemblerService {
    NuclearBindingConstraintGenerationDTO assembleBindingConstraints(TrajectoryEntity modulationTrajectory, List<String> frNuclearClusterNames);
}