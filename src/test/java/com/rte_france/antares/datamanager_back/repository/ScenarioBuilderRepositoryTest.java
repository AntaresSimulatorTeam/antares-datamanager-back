package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ScenarioBuilderEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

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
                .data("test_data")
                .trajectory(trajectory)
                .build();

        // When
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);

        // Then
        assertNotNull(saved.getId());
        assertEquals("test_data", saved.getData());
        assertEquals(trajectory.getId(), saved.getTrajectory().getId());
    }

    @Test
    void testFindByTrajectoryId_found() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .data("scenario_data_123")
                .trajectory(trajectory)
                .build();
        scenarioBuilderRepository.save(entity);
        entityManager.flush();

        // When
        Optional<ScenarioBuilderEntity> result = scenarioBuilderRepository.findByTrajectoryId(trajectory.getId());

        // Then
        assertTrue(result.isPresent());
        assertEquals("scenario_data_123", result.get().getData());
        assertEquals(trajectory.getId(), result.get().getTrajectory().getId());
    }

    @Test
    void testFindByTrajectoryId_notFound() {
        // Given
        Integer nonexistentTrajectoryId = 999999;

        // When
        Optional<ScenarioBuilderEntity> result = scenarioBuilderRepository.findByTrajectoryId(nonexistentTrajectoryId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testUpdateScenarioBuilder() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .data("initial_data")
                .trajectory(trajectory)
                .build();
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);
        Integer id = saved.getId();

        // When
        saved.setData("updated_data");
        scenarioBuilderRepository.save(saved);
        entityManager.flush();

        Optional<ScenarioBuilderEntity> updated = scenarioBuilderRepository.findById(id);

        // Then
        assertTrue(updated.isPresent());
        assertEquals("updated_data", updated.get().getData());
    }

    @Test
    void testDeleteScenarioBuilder() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .data("data_to_delete")
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
                .data("mapping_test_data")
                .trajectory(trajectory)
                .build();

        // When
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);
        entityManager.flush();
        entityManager.clear();

        ScenarioBuilderEntity retrieved = scenarioBuilderRepository.findById(saved.getId()).orElseThrow();

        // Then
        assertNotNull(retrieved.getId());
        assertEquals("mapping_test_data", retrieved.getData());
        assertNotNull(retrieved.getTrajectory());
        assertEquals(trajectory.getId(), retrieved.getTrajectory().getId());
        assertEquals("scenario_builder_test.xlsx", retrieved.getTrajectory().getFileName());
    }

    @Test
    void testMultipleScenarioBuilderForSameTrajectory_shouldReplace() {
        // Given
        ScenarioBuilderEntity first = ScenarioBuilderEntity.builder()
                .data("first_data")
                .trajectory(trajectory)
                .build();
        scenarioBuilderRepository.save(first);

        // When - save another with same trajectory (should be separate record)
        ScenarioBuilderEntity second = ScenarioBuilderEntity.builder()
                .data("second_data")
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
                .data(maxData)
                .trajectory(trajectory)
                .build();

        // When
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);
        entityManager.flush();

        // Then
        assertEquals(100, saved.getData().length());
    }

    @Test
    void testScenarioBuilderNullData() {
        // Given
        ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                .data(null)
                .trajectory(trajectory)
                .build();

        // When
        ScenarioBuilderEntity saved = scenarioBuilderRepository.save(entity);
        entityManager.flush();

        // Then
        assertNull(saved.getData());
    }
}
