package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.ScenarioBuilderRepository;
import com.rte_france.antares.datamanager_back.repository.model.ScenarioBuilderEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScenarioBuilderToJsonServiceTest {

    @Mock
    private ScenarioBuilderRepository scenarioBuilderRepository;

    @InjectMocks
    private ScenarioBuilderToJsonService scenarioBuilderToJsonService;

    private TrajectoryEntity trajectory;

    @BeforeEach
    void setUp() {
        trajectory = TrajectoryEntity.builder()
                .id(10)
                .fileName("scenario_builder.xlsx")
                .type("SCENARIO_BUILDER")
                .build();
    }

    @Test
    void testBuildScenarioBuilderMap_success() {
        // Given
        List<ScenarioBuilderEntity> entities = List.of(
                ScenarioBuilderEntity.builder().id(1).category("climatic_data").modulo("load").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(2).category("climatic_data").modulo("hydro").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(3).category("climatic_data").modulo("wind_onshore@*").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(4).category("thermal").modulo("nuclear@fr").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(5).category("thermal").modulo("*@z_p2g_asservi").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(6).category("links").modulo("nl/z_p2h_pachybride").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(7).category("sts_inflows").modulo("psp_closed@*").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(8).category("sts_constraints").modulo("TOTOTOTOTOTOTO@TOTOTO").trajectory(trajectory).build()
        );

        when(scenarioBuilderRepository.findByTrajectoryId(10)).thenReturn(entities);

        // When
        Map<String, Object> result = scenarioBuilderToJsonService.buildScenarioBuilderMap(10);

        // Then
        assertNotNull(result);
        assertEquals(5, result.size());

        assertTrue(result.containsKey("climatic_data"));
        assertEquals(List.of("load", "hydro", "wind_onshore@*"), result.get("climatic_data"));

        assertTrue(result.containsKey("thermal"));
        assertEquals(List.of("nuclear@fr", "*@z_p2g_asservi"), result.get("thermal"));

        assertTrue(result.containsKey("links"));
        assertEquals(List.of("nl/z_p2h_pachybride"), result.get("links"));

        assertTrue(result.containsKey("sts_inflows"));
        assertEquals(List.of("psp_closed@*"), result.get("sts_inflows"));

        assertTrue(result.containsKey("sts_constraints"));
        assertEquals(List.of("TOTOTOTOTOTOTO@TOTOTO"), result.get("sts_constraints"));

        verify(scenarioBuilderRepository, times(1)).findByTrajectoryId(10);
    }

    @Test
    void testBuildScenarioBuilderMap_nullTrajectoryId() {
        // When
        Map<String, Object> result = scenarioBuilderToJsonService.buildScenarioBuilderMap(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(scenarioBuilderRepository);
    }

    @Test
    void testBuildScenarioBuilderMap_emptyEntities() {
        // Given
        when(scenarioBuilderRepository.findByTrajectoryId(10)).thenReturn(Collections.emptyList());

        // When
        Map<String, Object> result = scenarioBuilderToJsonService.buildScenarioBuilderMap(10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(scenarioBuilderRepository, times(1)).findByTrajectoryId(10);
    }

    @Test
    void testBuildScenarioBuilderMap_nullEntities() {
        // Given
        when(scenarioBuilderRepository.findByTrajectoryId(10)).thenReturn(null);

        // When
        Map<String, Object> result = scenarioBuilderToJsonService.buildScenarioBuilderMap(10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(scenarioBuilderRepository, times(1)).findByTrajectoryId(10);
    }

    @Test
    void testBuildScenarioBuilderMap_handlesNullAndBlankCategoriesOrModulos() {
        // Given
        List<ScenarioBuilderEntity> entities = List.of(
                ScenarioBuilderEntity.builder().id(1).category(null).modulo("load").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(2).category("").modulo("hydro").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(3).category("   ").modulo("wind").trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(4).category("thermal").modulo(null).trajectory(trajectory).build(),
                ScenarioBuilderEntity.builder().id(5).category("thermal").modulo("nuclear@fr").trajectory(trajectory).build()
        );

        when(scenarioBuilderRepository.findByTrajectoryId(10)).thenReturn(entities);

        // When
        Map<String, Object> result = scenarioBuilderToJsonService.buildScenarioBuilderMap(10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(List.of("nuclear@fr"), result.get("thermal"));
    }
}
