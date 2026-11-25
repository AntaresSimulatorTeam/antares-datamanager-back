package com.rte_france.antares.datamanager_back.service.res;

import com.rte_france.antares.datamanager_back.dto.ResClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaKey;

import java.util.Map;

public interface ResGenerationAssemblerService {

    Map<AreaKey, Map<String, ResClusterGenerationDto>> assembleResProperties(StudyEntity studyEntity);
}
