package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterCapacityDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ThermalFileProcessorService {

     TrajectoryEntity processThermalCapacityFile(Path path, String horizon, ThermalClusterCapacityDto thermalClusterCapacityDto, TrajectoryType type, String area, String technology) throws IOException;

     TrajectoryEntity saveThermalCapacitiesTrajectory(TrajectoryEntity trajectory, ThermalClusterCapacityDto thermalClusterCapacityDto, TrajectoryType type);

     TrajectoryEntity saveThermalCommonTrajectory(TrajectoryEntity trajectory, List<ThermalCommonParameterEntity> thermalCommonParameterEntityList, TrajectoryType type);

     TrajectoryEntity saveThermalParamModulationTrajectory(TrajectoryEntity trajectory, List<ThermalModulationParameterEntity> thermalModulationParameterEntities, TrajectoryType type);

     TrajectoryEntity processThermalCommonParameterFile(Path path, String horizon, List<ThermalCommonParameterEntity> thermalCommonParameterEntities, TrajectoryType type) throws IOException;

     TrajectoryEntity processThermalModulationParameterFile(Path path, String horizon, List<ThermalModulationParameterEntity> thermalModulationParameterEntities, TrajectoryType type) throws IOException;

     ThermalClusterCapacityDto buildThermalClusterCapacityValuesList(Path path, String horizon, boolean isCivilYear, String area, String technology, Integer studyId) throws IOException;

     List<ThermalCommonParameterEntity> buildThermalCommonParameterValuesList(Path path, String horizon, Integer studyId) throws IOException;

     }
