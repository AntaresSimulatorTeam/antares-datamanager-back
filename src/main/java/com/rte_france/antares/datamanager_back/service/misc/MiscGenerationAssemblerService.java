package com.rte_france.antares.datamanager_back.service.misc;

import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;

import java.util.List;
import java.util.Map;

public interface MiscGenerationAssemblerService {

    Map<String, List<MiscGenerationDTO>> assembleMiscProperties(StudyEntity studyEntity);
}
