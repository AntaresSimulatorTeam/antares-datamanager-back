package com.rte_france.antares.datamanager_back.service.constraint_me;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface ConstraintMeFileProcessorService {

    TrajectoryEntity processConstraintMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException;
}
