package com.rte_france.antares.datamanager_back.service.sts;

import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;

import java.util.Map;

public interface StsPropertiesAssemblerService {

    Map<String, StsGenerationDTO> assembleStsProperties(StudyEntity studyEntity);
}
