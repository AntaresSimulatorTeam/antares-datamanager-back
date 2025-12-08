package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ThermalParamModulationService {

    TrajectoryEntity saveThermalParamModulationTrajectory(TrajectoryEntity trajectory, List<ThermalModulationParameterEntity> thermalModulationParameterEntities, TrajectoryType type);

    TrajectoryEntity processThermalModulationParameterFile(Path path, String horizon, List<ThermalModulationParameterEntity> thermalModulationParameterEntities, TrajectoryType type) throws IOException;

    List<String> createMatrixParamModulationTsFiles(StudyEntity study);

    void processThermalModulationSingleFile(String trajectoryToUse, String horizon, Integer studyId, Path trajectoryFilePath, String fileName, List<ThermalModulationParameterEntity> thermalModulationParameters, Path file, String fileType) throws IOException;

    void verifyExistingSpecificClustersOfParamModulation(String horizon, Integer studyId, Path modulationFile, String trajectoryName, String fileType) throws IOException;

    }
