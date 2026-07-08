package com.rte_france.antares.datamanager_back.service.adequacy;

import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;

import java.util.Map;
import java.util.Optional;

public interface AdequacySettingsAssemblerService {

    Optional<AdequacySettingsEntity> assembleAdequacySettings(StudyEntity studyEntity);

    Map<String, String> assembleAdequacyModeByArea(StudyEntity studyEntity);
}
