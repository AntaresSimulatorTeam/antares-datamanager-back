package com.rte_france.antares.datamanager_back.service.adequacy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.AdequacyModeEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AdequacySettingsAssemblerServiceImplTest {

    private AdequacySettingsAssemblerServiceImpl adequacySettingsAssemblerService;

    @BeforeEach
    void setUp() {
        adequacySettingsAssemblerService = new AdequacySettingsAssemblerServiceImpl();
    }

    @Test
    void assembleAdequacySettings_shouldReturnEmpty_whenNoTrajectories() {
        StudyEntity study = StudyEntity.builder().trajectories(new HashSet<>()).build();
        Optional<AdequacySettingsEntity> result = adequacySettingsAssemblerService.assembleAdequacySettings(study);
        assertThat(result).isEmpty();
    }

    @Test
    void assembleAdequacySettings_shouldReturnSettings_whenTrajectoryExists() {
        AdequacySettingsEntity settings = AdequacySettingsEntity.builder().id(1L).includeAdqPatch(true).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.ADEQUACY_PATCH.name())
                .adequacySettingsEntities(Collections.singletonList(settings))
                .build();
        
        StudyEntity study = new StudyEntity();
        study.setTrajectories(new HashSet<>(Collections.singletonList(trajectory)));

        Optional<AdequacySettingsEntity> result = adequacySettingsAssemblerService.assembleAdequacySettings(study);
        
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(settings);
    }

    @Test
    void assembleAdequacyModeByArea_shouldReturnEmptyMap_whenNoAdequacyTrajectory() {
        StudyEntity study = StudyEntity.builder().trajectories(new HashSet<>()).build();
        Map<String, String> result = adequacySettingsAssemblerService.assembleAdequacyModeByArea(study);
        assertThat(result).isEmpty();
    }

    @Test
    void assembleAdequacyModeByArea_shouldReturnModesMap_whenTrajectoryExists() {
        AdequacyModeEntity mode1 = AdequacyModeEntity.builder().area("Area1").mode("Mode1").build();
        AdequacyModeEntity mode2 = AdequacyModeEntity.builder().area("Area2").mode("Mode2").build();
        
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.ADEQUACY_PATCH.name())
                .adequacyModeEntities(Arrays.asList(mode1, mode2))
                .build();
        
        StudyEntity study = new StudyEntity();
        study.setTrajectories(new HashSet<>(Collections.singletonList(trajectory)));

        Map<String, String> result = adequacySettingsAssemblerService.assembleAdequacyModeByArea(study);
        
        assertThat(result).hasSize(2)
                .containsEntry("Area1", "Mode1")
                .containsEntry("Area2", "Mode2");
    }
}
