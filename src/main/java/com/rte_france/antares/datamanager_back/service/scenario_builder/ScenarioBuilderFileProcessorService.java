package com.rte_france.antares.datamanager_back.service.scenario_builder;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;

public interface ScenarioBuilderFileProcessorService {
    TrajectoryEntity processScenarioBuilderFile(String trajectoryToUse, String horizon, Integer studyId, String area) throws IOException;
}
