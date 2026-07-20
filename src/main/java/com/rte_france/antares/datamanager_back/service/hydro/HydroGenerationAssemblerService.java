package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.dto.HydroAreaGenerationDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;

import java.util.Map;

public interface HydroGenerationAssemblerService {
    Map<String, HydroAreaGenerationDTO> assembleHydroProperties(StudyEntity studyEntity) throws BusinessException;
}
