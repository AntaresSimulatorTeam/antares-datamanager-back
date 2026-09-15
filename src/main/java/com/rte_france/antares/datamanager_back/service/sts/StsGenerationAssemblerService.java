package com.rte_france.antares.datamanager_back.service.sts;

import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.util.List;
import java.util.Map;

public interface StsGenerationAssemblerService {

    Map<String, StsGenerationDTO> assembleStsProperties(StudyEntity studyEntity);

    Map<String, StsGenerationDTO> assembleStsMeProperties(StudyEntity studyEntity, TrajectoryEntity stsMeTrajectory);


    List<String> createMatrixStsTsFiles(StStorageEntity stsEntity, String horizon);
}
