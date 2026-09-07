package com.rte_france.antares.datamanager_back.service.adequacy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacyModeEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacySettingsAssemblerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

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
                .map(t -> {
                    Map<String, String> modeByArea = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    if (t.getAdequacyModeEntities() != null) {
                        for (AdequacyModeEntity entity : t.getAdequacyModeEntities()) {
                            if (entity.getArea() != null) {
                                modeByArea.putIfAbsent(entity.getArea(), entity.getMode());
                            }
                        }
                    }
                    return modeByArea;
                })
                .orElse(Collections.emptyMap());
    }

    @Override
    public Optional<TrajectoryEntity> findAdequacyTrajectory(StudyEntity studyEntity) {
        if (studyEntity.getTrajectories() == null) {
            return Optional.empty();
        }
        return studyEntity.getTrajectories().stream()
                .filter(t -> TrajectoryType.ADEQUACY_PATCH.name().equals(t.getType()))
                .findFirst();
    }

    @Override
    public Optional<String> resolveMode(String entityName, TrajectoryEntity adequacyTrajectory, Map<String, String> adequacyModeByArea) {
        if (adequacyTrajectory == null) {
            return Optional.empty();
        }
        String matchingKey = adequacyModeByArea.keySet().stream()
                .filter(key -> key.equalsIgnoreCase(entityName))
                .findFirst()
                .orElse(null);
        if (matchingKey == null) {
            throw BusinessException.builder()
                    .message("Area: {0} is not present in the list of areas for adequacy configuration , trajectory : {1}")
                    .errorMessageArguments(List.of(entityName, adequacyTrajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return Optional.ofNullable(adequacyModeByArea.get(matchingKey));
    }
}