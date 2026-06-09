package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface HydroFileProcessorService {
    TrajectoryEntity processHydroSeriesFile(TrajectoryType trajectoryType, String trajectoryToUse, String horizon, Integer studyId, String area, boolean isCivilYear) throws IOException;

    TrajectoryEntity processHydroTechnicalParametersFile(TrajectoryType trajectoryType, String trajectoryToUse, String horizon, Integer studyId, String area, boolean isCivilYear) throws IOException;
}