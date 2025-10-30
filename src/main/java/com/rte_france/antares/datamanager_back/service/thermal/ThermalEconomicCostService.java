package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.nio.file.Path;
import java.util.List;

public interface ThermalEconomicCostService {

    List<ThermalCostTypeEntity> buildThermalEconomicCostValueList(String trajectoryName, Path trajectoryFilePath, String horizon, Integer studyId);


    TrajectoryEntity saveThermalEconomicCostTrajectory(TrajectoryEntity trajectory, List<ThermalCostTypeEntity> thermalCostTypeEntities, TrajectoryType type);
}
