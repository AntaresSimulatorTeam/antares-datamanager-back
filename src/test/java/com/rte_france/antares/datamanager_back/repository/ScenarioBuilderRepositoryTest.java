package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ScenarioBuilderEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ScenarioBuilderRepositoryTest {

    @Autowired
    private ScenarioBuilderRepository scenarioBuilderRepository;

    @Autowired
    private TrajectoryRepository trajectoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private TrajectoryEntity trajectory;

    @BeforeEach
    void setUp() {
        trajectory = TrajectoryEntity.builder()
                .fileName("scenario_builder_test.xlsx")
                .type("SCENARIO_BUILDER")
                .fileSize(1024L)
                .checksum("abc123")
                .version(1)
                .createdBy("testuser")
                .horizon("2023-2024")
                .build();
        trajectoryRepository.saveAndFlush(trajectory);
    }

    @Test
    void testSaveScenarioBuilder() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .category("Default Rules")
                .modulo("test_modulo")
                .trajectory(trajectory)
                .build();

        // When
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);

        // Then
        assertNotNull(saved.getId());
        assertEquals("Default Rules", saved.getCategory());
        assertEquals("test_modulo", saved.getModulo());
        assertEquals(trajectory.getId(), saved.getTrajectory().getId());
    }

    @Test
    void testFindByTrajectoryId_found() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .category("Default Rules")
                .modulo("scenario_data_123")
                .trajectory(trajectory)
                .build();
        scenarioBuilderRepository.save(entity);
        entityManager.flush();

        // When
        List<ScenarioBuilderEntity> result = scenarioBuilderRepository.findByTrajectoryId(trajectory.getId());

        // Then
        assertFalse(result.isEmpty());
        assertEquals("Default Rules", result.get(0).getCategory());
        assertEquals("scenario_data_123", result.get(0).getModulo());
        assertEquals(trajectory.getId(), result.get(0).getTrajectory().getId());
    }

    @Test
    void testFindByTrajectoryId_notFound() {
        // Given
        Integer nonexistentTrajectoryId = 999999;

        // When
        List<ScenarioBuilderEntity> result = scenarioBuilderRepository.findByTrajectoryId(nonexistentTrajectoryId);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void testUpdateScenarioBuilder() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .category("Default Rules")
                .modulo("initial_modulo")
                .trajectory(trajectory)
                .build();
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);
        Integer id = saved.getId();

        // When
        saved.setModulo("updated_modulo");
        saved.setCategory("Hydro");
        scenarioBuilderRepository.save(saved);
        entityManager.flush();

        Optional<ScenarioBuilderEntity> updated = scenarioBuilderRepository.findById(id);

        // Then
        assertTrue(updated.isPresent());
        assertEquals("Hydro", updated.get().getCategory());
        assertEquals("updated_modulo", updated.get().getModulo());
    }

    @Test
    void testDeleteScenarioBuilder() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .category("Default Rules")
                .modulo("data_to_delete")
                .trajectory(trajectory)
                .build();
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);
        Integer id = saved.getId();

        // When
        scenarioBuilderRepository.deleteById(id);
        entityManager.flush();

        Optional<ScenarioBuilderEntity> result = scenarioBuilderRepository.findById(id);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testScenarioBuilderEntityMapping() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .category("Thermal")
                .modulo("mapping_test_modulo")
                .trajectory(trajectory)
                .build();

        // When
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        ScenarioBuilderEntity retrieved = scenarioBuilderRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertNotNull(retrieved.getId());
        assertEquals("Thermal", retrieved.getCategory());
        assertEquals("mapping_test_modulo", retrieved.getModulo());
        assertNotNull(retrieved.getTrajectory());
        assertEquals(trajectory.getId(), retrieved.getTrajectory().getId());
        assertEquals("scenario_builder_test.xlsx", retrieved.getTrajectory().getFileName());
    }

    @Test
    void testMultipleScenarioBuilderForSameTrajectory_shouldReplace() {
        // Given
        ScenarioBuilderEntity first = ScenarioBuilderEntity.builder()
                .category("Default Rules")
                .modulo("first_modulo")
                .trajectory(trajectory)
                .build();
        scenarioBuilderRepository.save(first);

        // When - save another with same trajectory (should be separate record)
        ScenarioBuilderEntity second = ScenarioBuilderEntity.builder()
                .category("Hydro")
                .modulo("second_modulo")
                .trajectory(trajectory)
                .build();
        scenarioBuilderRepository.save(second);
        entityManager.flush();

        // Then - both should exist with same trajectory
        var allByTrajectory = scenarioBuilderRepository.findAll().stream()
                .filter(sb -> sb.getTrajectory().getId().equals(trajectory.getId()))
                .toList();
        assertEquals(2, allByTrajectory.size());
    }

    @Test
    void testScenarioBuilderDataLengthLimit() {
        // Given - test with 100 character data (VARCHAR(100) limit)
        String maxData = "a".repeat(100);
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .category("Default Rules")
                .modulo(maxData)
                .trajectory(trajectory)
                .build();

        // When
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);
        entityManager.flush();

        // Then
        assertEquals(100, saved.getModulo().length());
    }

    @Test
    void testScenarioBuilderNullData() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .category("Default Rules")
                .modulo(null)
                .trajectory(trajectory)
                .build();

        // When
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);
        entityManager.flush();

        // Then
        assertNull(saved.getModulo());
        assertEquals("Default Rules", saved.getCategory());
    }
}
