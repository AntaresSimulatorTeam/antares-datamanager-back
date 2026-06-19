package com.rte_france.antares.datamanager_back.service.nuclear;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import java.io.IOException;
public interface NuclearFileProcessorService {
    /**
     * Process nuclear modulation file and save trajectory
     * @param trajectoryToUse the trajectory name
     * @param horizon the horizon in format yyyy-yyyy
     * @param studyId the study ID
     * @param area the area name
     * @return the saved trajectory entity
     * @throws IOException if an IO error occurs
     */
    TrajectoryEntity processNuclearModulationFile(String trajectoryToUse, String horizon, Integer studyId, String area) throws IOException;
}
