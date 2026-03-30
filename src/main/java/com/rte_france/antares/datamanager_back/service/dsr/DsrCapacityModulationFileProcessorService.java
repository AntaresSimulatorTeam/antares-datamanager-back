package com.rte_france.antares.datamanager_back.service.dsr;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

public interface DsrCapacityModulationFileProcessorService {
    TrajectoryEntity processDsrCapacityModulationFile(String trajectoryToUse, String horizon, Integer studyId) throws Exception;
}