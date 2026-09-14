package com.rte_france.antares.datamanager_back.service.sts;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface StStorageMeFileProcessorService {

    /**
     * Process ST_STORAGE_ME trajectory file and save to database.
     * STS_ME is a single-link trajectory type (only one instance per study).
     *
     * @param trajectoryToUse the trajectory name (must start with "me_")
     * @param horizon the study horizon in format "YYYY-YYYY+1"
     * @param studyId the study identifier
     * @return the saved TrajectoryEntity
     * @throws IOException if file operations fail
     */
    TrajectoryEntity processStStorageMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException;
}
