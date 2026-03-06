package com.rte_france.antares.datamanager_back.service.misc;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface MiscFileProcessorService {

    TrajectoryEntity processInstalledMiscFile(String trajectoryToUse, String horizon, Integer studyId, String area, boolean isCivilYear) throws IOException;

    TrajectoryEntity processLoadFactorMiscFile(String trajectoryToUse, String horizon, Integer studyId, String area) throws Exception;
}
