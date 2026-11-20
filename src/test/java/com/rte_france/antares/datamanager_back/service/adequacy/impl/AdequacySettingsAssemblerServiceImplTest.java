package com.rte_france.antares.datamanager_back.service.adequacy.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacyModeEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.settings.AdequacySettingsEntity;
import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        AdequacySettingsEntity settings = AdequacySettingsEntity.builder().id(1).includeAdqPatch(true).build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.ADEQUACY_PATCH.name())
                .adequacySettingsEntities(Collections.singletonList(settings))
                .build();

        StudyEntity study = new StudyEntity();
        study.setTrajectories(new HashSet<>(Collections.singletonList(trajectory)));

        Optional<AdequacySettingsEntity> result = adequacySettingsAssemblerService.assembleAdequacySettings(study);

        assertThat(result).isPresent().contains(settings);
    }

    @Test
    void assembleAdequacyModeByArea_shouldReturnEmptyMap_whenNoAdequacyTrajectory() {
        StudyEntity study = StudyEntity.builder().trajectories(new HashSet<>()).build();
        Map<AreaKey, String> result = adequacySettingsAssemblerService.assembleAdequacyModeByArea(study);
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

        Map<AreaKey, String> result = adequacySettingsAssemblerService.assembleAdequacyModeByArea(study);

        assertThat(result).hasSize(2)
                .containsEntry(AreaKey.of("Area1"), "Mode1")
                .containsEntry(AreaKey.of("Area2"), "Mode2");
        assertThat(result.get(AreaKey.of("area1"))).isEqualTo("Mode1");
        assertThat(result.get(AreaKey.of("AREA2"))).isEqualTo("Mode2");
    }

    @Test
    void assembleAdequacyModeByArea_shouldHandleNullEntitiesAndNullModes() {
        AdequacyModeEntity modeWithNullMode = AdequacyModeEntity.builder().area("AreaNull").mode(null).build();
        AdequacyModeEntity entityWithNullArea = AdequacyModeEntity.builder().area(null).mode("Mode").build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.ADEQUACY_PATCH.name())
                .adequacyModeEntities(Arrays.asList(modeWithNullMode, entityWithNullArea))
                .build();

        StudyEntity study = new StudyEntity();
        study.setTrajectories(new HashSet<>(Collections.singletonList(trajectory)));

        Map<AreaKey, String> result = adequacySettingsAssemblerService.assembleAdequacyModeByArea(study);

        assertThat(result).hasSize(1)
                .containsEntry(AreaKey.of("AreaNull"), null);
    }

    @Test
    void assembleAdequacyModeByArea_shouldIgnoreBlankAreas() {
        AdequacyModeEntity blank = AdequacyModeEntity.builder().area(" ").mode("Mode").build();
        AdequacyModeEntity valid = AdequacyModeEntity.builder().area("DEkf").mode("inside").build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.ADEQUACY_PATCH.name())
                .adequacyModeEntities(Arrays.asList(blank, valid))
                .build();
        StudyEntity study = new StudyEntity();
        study.setTrajectories(new HashSet<>(Collections.singletonList(trajectory)));

        assertThat(adequacySettingsAssemblerService.assembleAdequacyModeByArea(study)).containsOnlyKeys(AreaKey.of("DEkf"));
    }

    @Test
    void assembleAdequacyModeByArea_shouldKeepTheFirstModeWhenTheSameAreaIsWrittenWithAnotherCase() {
        AdequacyModeEntity first = AdequacyModeEntity.builder().area("DEkf").mode("inside").build();
        AdequacyModeEntity second = AdequacyModeEntity.builder().area("DEKF").mode("outside").build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.ADEQUACY_PATCH.name())
                .adequacyModeEntities(Arrays.asList(first, second))
                .build();
        StudyEntity study = new StudyEntity();
        study.setTrajectories(new HashSet<>(Collections.singletonList(trajectory)));

        Map<AreaKey, String> result = adequacySettingsAssemblerService.assembleAdequacyModeByArea(study);

        assertThat(result).hasSize(1);
        assertThat(result.get(AreaKey.of("dekf"))).isEqualTo("inside");
    }

    @Test
    void resolveMode_shouldFindTheModeWhateverTheCaseOfTheArea() {
        TrajectoryEntity trajectory = TrajectoryEntity.builder().fileName("adequacy").build();
        Map<AreaKey, String> modeByArea = Map.of(AreaKey.of("DEkf"), "inside");

        assertThat(adequacySettingsAssemblerService.resolveMode("DEkf", trajectory, modeByArea)).contains("inside");
        assertThat(adequacySettingsAssemblerService.resolveMode("DEKF", trajectory, modeByArea)).contains("inside");
        assertThat(adequacySettingsAssemblerService.resolveMode(" dekf ", trajectory, modeByArea)).contains("inside");
    }

    @Test
    void resolveMode_shouldThrowWithTheOriginalNameWhenTheAreaIsMissing() {
        TrajectoryEntity trajectory = TrajectoryEntity.builder().fileName("adequacy").build();

        assertThatThrownBy(() -> adequacySettingsAssemblerService.resolveMode("DEkf", trajectory, Map.of(AreaKey.of("FR"), "inside")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorMessageArguments()).containsExactly("DEkf", "adequacy"));
    }
}
