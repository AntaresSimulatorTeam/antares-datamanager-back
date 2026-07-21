package com.rte_france.antares.datamanager_back.service.adequacy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacyModeEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacySettingsAssemblerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdequacySettingsAssemblerServiceImpl implements AdequacySettingsAssemblerService {

    @Override
    public Optional<AdequacySettingsEntity> assembleAdequacySettings(StudyEntity studyEntity) {
        return findAdequacyTrajectory(studyEntity)
                .flatMap(t -> t.getAdequacySettingsEntities().stream().findFirst());
    }

    @Override
    public Map<String, String> assembleAdequacyModeByArea(StudyEntity studyEntity) {
        return findAdequacyTrajectory(studyEntity)
                .map(t -> t.getAdequacyModeEntities().stream()
                        .collect(Collectors.toMap(AdequacyModeEntity::getArea, AdequacyModeEntity::getMode, (a, b) -> a)))
                .orElse(Collections.emptyMap());
    }

    private Optional<TrajectoryEntity> findAdequacyTrajectory(StudyEntity studyEntity) {
        if (studyEntity.getTrajectories() == null) {
            return Optional.empty();
        }
        return studyEntity.getTrajectories().stream()
                .filter(t -> TrajectoryType.ADEQUACY_PATCH.name().equals(t.getType()))
                .findFirst();
    }
}
