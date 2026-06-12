package com.rte_france.antares.datamanager_back.service.res;

import com.rte_france.antares.datamanager_back.dto.ResClusterGenerationDto;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;

import java.util.Map;

public interface ResGenerationAssemblerService {

    Map<String, Map<String, ResClusterGenerationDto>> assembleResProperties(StudyEntity studyEntity);
}
