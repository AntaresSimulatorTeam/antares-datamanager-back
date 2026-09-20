package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface HydroMeFileProcessorService {
    TrajectoryEntity processHydroCapacityMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException;
}
