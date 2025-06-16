package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface LoadFileProcessorService {
    /**
     * Processes a load file and returns a trajectory entity.
     *
     * @param path the path to the load file
     * @return the processed trajectory entity
     * @throws IOException if an I/O error occurs
     */
  String saveMatrixToNas(Path path) throws IOException;
  /**
   * Processes a load file and returns a trajectory entity.
   *
   * @param trajectoryPath the path to the trajectory file
   * @param horizon the horizon for the trajectory
   * @param studyId the study ID
   * @return the processed trajectory entity
   * @throws IOException if an I/O error occurs
   */
  Set<WarningMessageEntity> checkForMissingLoadFiles(Path trajectoryPath, String horizon, Integer studyId, String userNni, TrajectoryEntity trajectory);
  /**
   * Checks for missing load files in the database.
   *
   * @param horizon the horizon to check
   * @param studyId the study ID
   * @param userNni the user NNI
   * @param trajectory the trajectory entity
   * @return a set of warning messages for missing load files
   */
  Set<WarningMessageEntity> checkForMissingLoadByAreaFromDb(String horizon, Integer studyId, String userNni, TrajectoryEntity trajectory, Path trajectoryPath) throws IOException;
}
