package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalCostsRateEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.nio.file.Path;
import java.util.List;

public interface ThermalEconomicCostAndRateService {

    List<ThermalCostTypeEntity> buildThermalEconomicCostValueList(String trajectoryName, Path trajectoryFilePath, String horizon, Integer studyId);

    List<ThermalCostsRateEntity> buildThermalEconomicRateValueList(String trajectoryName, Path trajectoryFilePath,String horizon, Integer studyId);

    TrajectoryEntity saveThermalEconomicCostAndRateTrajectory(TrajectoryEntity trajectory, List<ThermalCostTypeEntity> thermalCostTypeEntities,  List<ThermalCostsRateEntity> thermalCostsRateEntities, TrajectoryType trajectoryType);
}
