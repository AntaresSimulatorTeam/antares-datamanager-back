package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface ThermalFileProcessorService {

     TrajectoryEntity processThermalCapacityFile(File file,String horizon) throws IOException ;

     TrajectoryEntity processThermalParameterFile(File file,String horizon) throws IOException ;

     TrajectoryEntity processThermalCostFile(File file, String horizon) throws IOException ;
     TrajectoryEntity saveThermalCapacitiesTrajectory(TrajectoryEntity trajectory, List<ThermalClusterCapacityEntity> thermalClusterCapacities);
     TrajectoryEntity saveThermalParametersTrajectory(TrajectoryEntity trajectory, List<ThermalParameterEntity> thermalParameterEntities);

     TrajectoryEntity saveThermalCostTrajectory(TrajectoryEntity trajectory, List<ThermalCostEntity> thermalCostEntities);

    }
