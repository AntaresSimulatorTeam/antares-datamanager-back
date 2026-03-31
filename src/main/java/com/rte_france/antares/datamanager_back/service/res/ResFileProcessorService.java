package com.rte_france.antares.datamanager_back.service.res;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import java.io.IOException;

public interface ResFileProcessorService {
    TrajectoryEntity processInstalledResFile(String trajectoryToUse, String horizon, Integer studyId, String area, String technology, boolean isCivilYear) throws IOException;

    TrajectoryEntity processLoadFactorResFile(String trajectoryToUse, String horizon, Integer studyId, String area, String technology) throws IOException;
    
    TrajectoryEntity processTechnologyDistributionResFile(String trajectoryToUse, String horizon, Integer studyId, String area, String technology, boolean isCivilYear) throws IOException;

    TrajectoryEntity processZonalDistributionResFile(String trajectoryToUse, String horizon, Integer studyId, String area, String technology, boolean isCivilYear) throws IOException;
}
