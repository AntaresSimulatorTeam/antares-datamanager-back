package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface HydroParametersMeFileProcessorService {
    TrajectoryEntity processHydroParametersMeDirectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException;
}
