package com.rte_france.antares.datamanager_back.service.res;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

public interface ResFileProcessorService {
    TrajectoryEntity processInstalledResFile(String trajectoryToUse, String horizon, Integer studyId, String area, String technology, boolean isCivilYear) throws Exception;

    TrajectoryEntity processLoadFactorResFile(String trajectoryToUse, String horizon, Integer studyId, String area, String technology) throws Exception;
    
    TrajectoryEntity processTechnologyDistributionResFile(String trajectoryToUse, String horizon, Integer studyId, String area, String technology, boolean isCivilYear) throws Exception;
}
