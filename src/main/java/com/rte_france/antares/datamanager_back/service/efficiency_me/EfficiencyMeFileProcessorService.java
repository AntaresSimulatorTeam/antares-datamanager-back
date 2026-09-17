package com.rte_france.antares.datamanager_back.service.efficiency_me;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import java.io.IOException;

public interface EfficiencyMeFileProcessorService {
    TrajectoryEntity processEfficiencyMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException;
}
