package com.rte_france.antares.datamanager_back.service.dsr;

import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;

import java.util.List;
import java.util.Map;

public interface DsrGenerationAssemblerService {

    Map<String, DsrGenerationDTO> assembleDsrProperties(StudyEntity studyEntity);

    List<String> createMatrixDsrTsFiles(StudyEntity studyEntity);
}
