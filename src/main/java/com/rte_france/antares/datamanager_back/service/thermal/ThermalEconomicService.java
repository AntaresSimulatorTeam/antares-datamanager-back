package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicCo2Entity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicEnerContentEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ThermalEconomicService {

    List<ThermalEconomicCo2Entity> buildThermalEconomicCo2ParameterValuesList(Path trajectoryFilePath, String horizon, Integer studyId) throws IOException;

    List<ThermalEconomicEnerContentEntity> buildThermalEconomicEnerContentParameterValuesList(Path trajectoryFilePath, String horizon, Integer studyId) throws IOException;

    TrajectoryEntity processThermalEconomicParameterFile(Path trajectoryFilePath, String horizon, List<ThermalEconomicCo2Entity> thermalEconomicCo2Entities,List<ThermalEconomicEnerContentEntity> thermalEconomicEnerContentEntities , TrajectoryType trajectoryType) throws IOException;

}

