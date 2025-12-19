package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicCo2Entity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalEconomicEnerContentEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.apache.poi.ss.usermodel.Sheet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ThermalEconomicService {

    List<ThermalEconomicCo2Entity> buildThermalEconomicCo2ParameterValuesList(String fileName, String horizon, Integer studyId, Sheet co2Sheet) throws IOException;

    List<ThermalEconomicEnerContentEntity> buildThermalEconomicEnerContentParameterValuesList(String fileName, String horizon, Integer studyId, Sheet enerSheet) throws IOException;

    TrajectoryEntity processThermalEconomicParameterFile(Path trajectoryFilePath, String horizon, List<ThermalEconomicCo2Entity> thermalEconomicCo2Entities,List<ThermalEconomicEnerContentEntity> thermalEconomicEnerContentEntities , TrajectoryType trajectoryType) throws IOException;

}

