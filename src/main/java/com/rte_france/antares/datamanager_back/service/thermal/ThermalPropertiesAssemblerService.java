package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.ThermalMEClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.util.Map;

public interface ThermalPropertiesAssemblerService {

    Map<AreaClusterRefKey, ThermalClusterGenerationDto> assembleForTrajectories(StudyEntity study);

    Map<String, ThermalMEClusterGenerationDto> assembleThermalMeProperties(TrajectoryEntity trajectoryEntity);

}
