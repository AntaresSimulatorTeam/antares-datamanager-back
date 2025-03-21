package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;
import java.nio.file.Path;

public interface LoadFileProcessorService {
  TrajectoryEntity processLoadFile(Path path, String horizon) throws IOException;
}
