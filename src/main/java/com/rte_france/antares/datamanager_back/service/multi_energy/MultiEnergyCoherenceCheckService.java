package com.rte_france.antares.datamanager_back.service.multi_energy;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

public interface MultiEnergyCoherenceCheckService {
    
    void checkAreaMETrajectoryConsistency(Integer studyId, String trajectoryType, TrajectoryEntity trajectory);
}
