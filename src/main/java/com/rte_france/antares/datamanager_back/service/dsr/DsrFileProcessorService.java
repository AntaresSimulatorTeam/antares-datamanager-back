package com.rte_france.antares.datamanager_back.service.dsr;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface DsrFileProcessorService {
    TrajectoryEntity processDsrClusterFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String area) throws Exception;
}