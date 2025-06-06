package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface LoadFileProcessorService {
  String saveMatrixToNas(Path path) throws IOException;
  Set<WarningMessageEntity> checkForMissingLoadFiles(Path trajectoryPath, String horizon, Integer studyId, String userNni, TrajectoryEntity trajectory);
}
