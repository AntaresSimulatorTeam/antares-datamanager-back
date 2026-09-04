package com.rte_france.antares.datamanager_back.service.multi_energy;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacySettingsAssemblerService;
import com.rte_france.antares.datamanager_back.service.multi_energy.impl.MultiEnergyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiEnergyServiceImplTest {

    @Mock
    private AdequacySettingsAssemblerService adequacySettingsAssemblerService;

    @InjectMocks
    private MultiEnergyServiceImpl multiEnergyService;

    private StudyEntity studyEntity;
    private TrajectoryEntity areaMeTrajectory;

    @BeforeEach
    void setUp() {
        AreaEntity areaEntity = AreaEntity.builder()
                .name("area_me")
                .x(1.0)
                .y(2.0)
                .r(1.0)
                .g(2.0)
                .b(3.0)
                .build();

        AreaConfigEntity areaConfigEntity = AreaConfigEntity.builder()
                .district("district_me")
                .unsuppliedEnergyCost(4000.0)
                .spilledEnergyCost(200.0)
                .area(areaEntity)
                .build();

        areaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .fileName("area_me.xlsx")
                .areaConfigEntities(List.of(areaConfigEntity))
                .build();

        studyEntity = StudyEntity.builder()
                .id(1)
                .name("testStudy")
                .trajectories(Set.of(areaMeTrajectory))
                .build();
    }

    @Test
    void buildMultiEnergyMap_withAdequacyMode_shouldReturnExpectedStructure() {
        // Given
        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(any()))
                .thenReturn(Map.of("area_me", "outside"));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areaData = (Map<String, Object>) result.get("area_me");
        assertThat(areaData).containsEntry("ui", "AreaUI class as JSON");
        assertThat(areaData).containsKey("properties");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) areaData.get("properties");
        assertThat(properties)
                .containsEntry("energy_cost_unsupplied", 4000.0)
                .containsEntry("energy_cost_spilled", 200.0)
                .containsEntry("adequacy_patch_mode", "outside");
    }

    @Test
    void buildMultiEnergyMap_withoutAdequacyMode_shouldReturnNullAdequacyPatchMode() {
        // Given
        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(any()))
                .thenReturn(Collections.emptyMap());

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, areaMeTrajectory);

        // Then
        assertThat(result).isNotNull().containsKey("area_me");

        @SuppressWarnings("unchecked")
        Map<String, Object> areaData = (Map<String, Object>) result.get("area_me");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) areaData.get("properties");

        assertThat(properties)
                .containsEntry("energy_cost_unsupplied", 4000.0)
                .containsEntry("energy_cost_spilled", 200.0)
                .containsEntry("adequacy_patch_mode", null);
    }

    @Test
    void buildMultiEnergyMap_withStudyOverload_shouldFindAreaMeTrajectoryAndBuildMap() {
        // Given
        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(any()))
                .thenReturn(Map.of("area_me", "outside"));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity);

        // Then
        assertThat(result).isNotNull().containsKey("area_me");
    }

    @Test
    void buildMultiEnergyMap_whenTrajectoryOrConfigsNull_shouldReturnEmptyMap() {
        assertThat(multiEnergyService.buildMultiEnergyMap(studyEntity, null)).isEmpty();

        TrajectoryEntity emptyTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(null)
                .build();
        assertThat(multiEnergyService.buildMultiEnergyMap(studyEntity, emptyTrajectory)).isEmpty();

        assertThat(multiEnergyService.buildMultiEnergyMap(null)).isEmpty();

        StudyEntity studyWithoutTrajectories = StudyEntity.builder().id(2).name("empty").trajectories(null).build();
        assertThat(multiEnergyService.buildMultiEnergyMap(studyWithoutTrajectories)).isEmpty();
    }

    @Test
    void buildMultiEnergyMap_multipleAreas_shouldReturnAllAreas() {
        // Given
        AreaEntity area1 = AreaEntity.builder().name("AREA1_ME").build();
        AreaEntity area2 = AreaEntity.builder().name("AREA2_ME").build();

        AreaConfigEntity config1 = AreaConfigEntity.builder()
                .area(area1)
                .unsuppliedEnergyCost(3000.0)
                .spilledEnergyCost(100.0)
                .build();

        AreaConfigEntity config2 = AreaConfigEntity.builder()
                .area(area2)
                .unsuppliedEnergyCost(5000.0)
                .spilledEnergyCost(300.0)
                .build();

        TrajectoryEntity multipleAreaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(List.of(config1, config2))
                .build();

        when(adequacySettingsAssemblerService.assembleAdequacyModeByArea(any()))
                .thenReturn(Map.of("AREA1_ME", "inside", "AREA2_ME", "outside"));

        // When
        Map<String, Object> result = multiEnergyService.buildMultiEnergyMap(studyEntity, multipleAreaMeTrajectory);

        // Then
        assertThat(result).hasSize(2).containsKeys("AREA1_ME", "AREA2_ME");

        @SuppressWarnings("unchecked")
        Map<String, Object> area1Data = (Map<String, Object>) result.get("AREA1_ME");
        @SuppressWarnings("unchecked")
        Map<String, Object> area1Props = (Map<String, Object>) area1Data.get("properties");
        assertThat(area1Props)
                .containsEntry("energy_cost_unsupplied", 3000.0)
                .containsEntry("energy_cost_spilled", 100.0)
                .containsEntry("adequacy_patch_mode", "inside");

        @SuppressWarnings("unchecked")
        Map<String, Object> area2Data = (Map<String, Object>) result.get("AREA2_ME");
        @SuppressWarnings("unchecked")
        Map<String, Object> area2Props = (Map<String, Object>) area2Data.get("properties");
        assertThat(area2Props)
                .containsEntry("energy_cost_unsupplied", 5000.0)
                .containsEntry("energy_cost_spilled", 300.0)
                .containsEntry("adequacy_patch_mode", "outside");
    }
}
