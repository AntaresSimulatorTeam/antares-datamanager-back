package com.rte_france.antares.datamanager_back.service.multi_energy;

import com.rte_france.antares.datamanager_back.dto.ConstraintRowData;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.util.Set;

public interface MultiEnergyCoherenceCheckService {
    
    void checkAreaMETrajectoryConsistency(Integer studyId, String trajectoryType, TrajectoryEntity trajectory);

    Set<String> extractNodesFromAreaMeTrajectory(Integer studyId);

    void validateHydroCapacityMeCoherence(Integer studyId, TrajectoryEntity trajectoryEntity);

    void validateConstraintMeCoherence(Integer studyId, TrajectoryEntity trajectory);

    void validateConstraintData(ConstraintRowData data, Set<String> areaMeNodes, Set<String> groupAreaNames, Set<String> groupClusterNames, String horizonYear);

}
