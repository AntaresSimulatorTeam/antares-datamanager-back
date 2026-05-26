package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface HydroGenerationAssemblerService {
    Map<String, List<HydroGenerationDTO>> assembleHydroProperties(StudyEntity studyEntity) throws BusinessException;
}
