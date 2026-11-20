package com.rte_france.antares.datamanager_back.service.adequacy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacyModeEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacySettingsAssemblerService;
import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaKey;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AdequacySettingsAssemblerServiceImpl implements AdequacySettingsAssemblerService {

    @ExecutionTime("ADEQUACY_PATCH")
    @Override
    public Optional<AdequacySettingsEntity> assembleAdequacySettings(StudyEntity studyEntity) {
        return findAdequacyTrajectory(studyEntity)
                .flatMap(t -> t.getAdequacySettingsEntities().stream().findFirst());
    }

    @Override
    public Map<AreaKey, String> assembleAdequacyModeByArea(StudyEntity studyEntity) {
        return findAdequacyTrajectory(studyEntity)
                .map(t -> {
                    Map<AreaKey, String> modeByArea = new LinkedHashMap<>();
                    if (t.getAdequacyModeEntities() != null) {
                        for (AdequacyModeEntity entity : t.getAdequacyModeEntities()) {
                            if (StringUtils.isNotBlank(entity.getArea())) {
                                modeByArea.putIfAbsent(AreaKey.of(entity.getArea()), entity.getMode());
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
    public Optional<String> resolveMode(String entityName, TrajectoryEntity adequacyTrajectory, Map<AreaKey, String> adequacyModeByArea) {
        if (adequacyTrajectory == null) {
            return Optional.empty();
        }
        AreaKey key = AreaKey.of(entityName);
        if (!adequacyModeByArea.containsKey(key)) {
            throw BusinessException.builder()
                    .message("Area: {0} is not present in the list of areas for adequacy configuration , trajectory : {1}")
                    .errorMessageArguments(List.of(entityName, adequacyTrajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return Optional.ofNullable(adequacyModeByArea.get(key));
    }
}