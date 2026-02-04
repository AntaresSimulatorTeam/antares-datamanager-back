package com.rte_france.antares.datamanager_back.service.sts;

import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;

import java.util.List;
import java.util.Map;

public interface StsGenerationAssemblerService {

    Map<String, StsGenerationDTO> assembleStsProperties(StudyEntity studyEntity);

    List<String> createMatrixStsTsFiles(StStorageEntity stsEntity, String horizon);
}
