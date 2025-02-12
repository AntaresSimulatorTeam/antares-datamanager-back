package com.rte_france.antares.datamanager_back.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;

import java.io.IOException;
import java.nio.file.Path;

public interface StudyGeneratorService {
    void studyTobeGenerated(StudyDTO studyDTO) throws JsonProcessingException;
}
