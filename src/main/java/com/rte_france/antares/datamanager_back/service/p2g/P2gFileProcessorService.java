package com.rte_france.antares.datamanager_back.service.p2g;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import java.io.IOException;

public interface P2gFileProcessorService {
    TrajectoryEntity processCapacityP2gFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear) throws IOException;
    
    TrajectoryEntity processModulationP2gFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear) throws IOException;

}
