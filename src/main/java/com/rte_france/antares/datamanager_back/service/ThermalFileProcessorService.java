package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

public interface ThermalFileProcessorService {
     TrajectoryEntity processThermalFile(File file, String horizon, ThermalFileProcessorServiceImpl.ThermalBuilder builder, TrajectoryType type) throws IOException;
     TrajectoryEntity saveThermalTrajectory(TrajectoryEntity trajectory, List<? extends ThermalBaseEntity> thermalEntities, TrajectoryType type);

     List<ThermalClusterCapacityEntity> buildThermalClusterCapacityValuesList(File file) throws IOException;
     List<ThermalCostEntity> buildThermalCosts(File file) throws IOException;
     List<ThermalParameterEntity> buildThermalParameters(File file) throws IOException;

    }
