package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ThermalFileProcessorService {
     TrajectoryEntity processThermalCapacityFile(Path path, String horizon, List<ThermalClusterCapacityEntity> thermalClusterCapacityEntityList, TrajectoryType type) throws IOException;

     TrajectoryEntity processThermalParametersFile(Path path, String horizon, List<ThermalParameterEntity> thermalParameterEntityList, TrajectoryType type) throws IOException;

     TrajectoryEntity processThermalCostFile(Path path, String horizon, List<ThermalCostEntity> thermalCostEntityList, TrajectoryType type) throws IOException;

     TrajectoryEntity saveThermalTrajectory(TrajectoryEntity trajectory, List<? extends ThermalBaseEntity> thermalEntities, TrajectoryType type);

     List<ThermalClusterCapacityEntity> buildThermalClusterCapacityValuesList(Path path, String horizon, boolean isCivilYear, String area) throws IOException;

     List<ThermalCostEntity> buildThermalCosts(Path path) throws IOException;

     List<ThermalParameterEntity> buildThermalParameters(Path path) throws IOException;

    }
