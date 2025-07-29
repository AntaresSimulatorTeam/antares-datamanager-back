package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_test_trajectories.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql")
class StudyTrajectoryRepositoryTest {

    @Autowired
    private StudyTrajectoryRepository studyTrajectoryRepository;

    @Test
    void findById_ScenarioId_returnsEntitiesForExistingScenarioId() {
        var scenarioId = 1;
        var result = studyTrajectoryRepository.findById_ScenarioId(scenarioId);

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(e -> e.getId().getScenarioId().equals(scenarioId));
    }

    @Test
    void findById_ScenarioId_returnsEmptyListForNonExistentScenarioId() {
        var scenarioId = 9999;
        var result = studyTrajectoryRepository.findById_ScenarioId(scenarioId);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}