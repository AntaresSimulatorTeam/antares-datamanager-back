package com.rte_france.antares.datamanager_back.service.adequacy;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface AdequacyFileProcessorService {

    TrajectoryEntity processAdequacyFile(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear) throws IOException;

}
