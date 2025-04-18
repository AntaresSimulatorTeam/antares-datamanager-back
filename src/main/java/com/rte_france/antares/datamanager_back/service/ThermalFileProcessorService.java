package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ThermalFileProcessorService {
     TrajectoryEntity processThermalFile(Path path, String horizon, ThermalBuilder builder, TrajectoryType type) throws IOException;
     TrajectoryEntity saveThermalTrajectory(TrajectoryEntity trajectory, List<? extends ThermalBaseEntity> thermalEntities, TrajectoryType type);

     List<ThermalClusterCapacityEntity> buildThermalClusterCapacityValuesList(Path path) throws IOException;
     List<ThermalCostEntity> buildThermalCosts(Path path) throws IOException;
     List<ThermalParameterEntity> buildThermalParameters(Path path) throws IOException;

    }
