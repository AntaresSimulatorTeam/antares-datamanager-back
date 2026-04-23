package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface HydroFileProcessorService {
    TrajectoryEntity processHydroSeriesFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String area) throws IOException;
}