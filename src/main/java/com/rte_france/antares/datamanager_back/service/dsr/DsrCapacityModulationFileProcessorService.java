package com.rte_france.antares.datamanager_back.service.dsr;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface DsrCapacityModulationFileProcessorService {
    TrajectoryEntity processDsrCapacityModulationFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException;
    void validateDsrCapacityModulationCoherence(TrajectoryEntity trajectory, Integer studyId) throws IOException;
}