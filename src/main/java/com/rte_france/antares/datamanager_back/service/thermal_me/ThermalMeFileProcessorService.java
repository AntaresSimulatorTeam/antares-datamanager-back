package com.rte_france.antares.datamanager_back.service.thermal_me;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import java.io.IOException;

public interface ThermalMeFileProcessorService {

    TrajectoryEntity processThermalMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException; 
}
