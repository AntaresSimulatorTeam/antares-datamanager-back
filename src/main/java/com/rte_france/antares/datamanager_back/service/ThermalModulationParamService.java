package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;

import java.nio.file.Path;
import java.util.Set;

public interface ThermalModulationParamService {

    TrajectoryEntity saveParamModulationToDb(String trajectoryToUse, String horizon, Integer studyId);

    TrajectoryEntity buildAndSaveModulationParamTrajectory(String horizon, String TrajectoryName, Path trajectoryPath, TrajectoryEntity loadTrajectory, Integer studyId, Set<WarningMessageEntity> warningMessageEntities);

}
