package com.rte_france.antares.datamanager_back.service.sts;

import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.util.List;
import java.util.Map;

public interface StsGenerationAssemblerService {

    /**
     * Shared input series reference the same generated NAS file within this assembly.
     * Independent files are processed with bounded concurrency without accessing entities from worker threads.
     */
    Map<String, StsGenerationDTO> assembleStsProperties(StudyEntity studyEntity);

    /**
     * Generates one NAS file per input file and matching cluster column, preserving required-series order.
     */
    Map<String, StsGenerationDTO> assembleStsMeProperties(StudyEntity studyEntity, TrajectoryEntity stsMeTrajectory);

    List<String> createMatrixStsTsFiles(StStorageEntity stsEntity, String horizon);
}
