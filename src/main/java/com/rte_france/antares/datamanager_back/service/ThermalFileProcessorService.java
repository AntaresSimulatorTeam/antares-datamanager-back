package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ThermalFileProcessorService {

     TrajectoryEntity processThermalCapacityFile(Path path, String horizon, List<ThermalClusterCapacityEntity> thermalClusterCapacityEntityList, TrajectoryType type, String area, String technology) throws IOException;

     TrajectoryEntity processThermalCommonParameterFile(Path path, String horizon, List<ThermalCommonParameterEntity> thermalCommonParameterEntities, TrajectoryType type) throws IOException;

     TrajectoryEntity saveThermalTrajectory(TrajectoryEntity trajectory, List<? extends ThermalBaseEntity> thermalEntities, TrajectoryType type);

     List<ThermalClusterCapacityEntity> buildThermalClusterCapacityValuesList(Path path, String horizon, boolean isCivilYear, String area, String technology) throws IOException;

     List<ThermalCommonParameterEntity> buildThermalCommonParameterValuesList(Path path, String horizon, boolean isCivilYear) throws IOException;

    }
