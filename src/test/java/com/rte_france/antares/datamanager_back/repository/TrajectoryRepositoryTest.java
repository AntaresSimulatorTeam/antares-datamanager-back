package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_db.sql")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "classpath:db/init_test_trajectories.sql")
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:db/clean_db.sql")
class TrajectoryRepositoryTest {


    @Autowired
    private TrajectoryRepository trajectoryRepository;

    @Test
    void findFirstByFileNameOrderByVersionDesc_returnsTrajectoryEntity() {

        Optional<TrajectoryEntity> foundEntity = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("testFile.txt", "2023-2024", "AREA");

        assertThat(foundEntity).isPresent();
        assertThat(foundEntity.get().getFileName()).isEqualTo("testFile.txt");
        assertThat(foundEntity.get().getVersion()).isEqualTo(1);
    }

    @Test
    void findFirstByFileNameOrderByVersionDesc_returnsEmptyOptionalForNonExistentFile() {
        Optional<TrajectoryEntity> foundEntity = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc("nonExistentFile.txt", "2023-2024", "AREA");
        assertThat(foundEntity).isNotPresent();
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartsWith_returnsEmptyListForNonExistentType() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains("nonExistentType", "2023-2024", "test","FR","technology1");
        assertThat(trajectoryEntities).isEmpty();
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartsWith_returnsEmptyListForNonExistentFileNameStartsWith() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains("AREA", "2023-2024", "nonExistentStart", "FR","technology1");
        assertThat(trajectoryEntities).isEmpty();
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartsWith_returnsNonEmptyListForExistentTypeAndFileNameStartsWith() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains("AREA", "2023-2024", "test", "FR","technology1");
        assertThat(trajectoryEntities).isNotEmpty();
        assertThat(trajectoryEntities.getFirst().getFileName()).startsWith("test");
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartsWith_returnsEmptyListForNullType() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(null, "2023-2024", "test", "FR","technology1");
        assertThat(trajectoryEntities).isEmpty();
    }

    @Test
    void findByTypeAndStudyId_returnsNonEmptyListForExistentTypeAndStudyId() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), 1);
        assertThat(trajectoryEntities).isNotEmpty();
        assertThat(trajectoryEntities.getFirst().getType()).isEqualTo("AREA");
    }

    @Test
    void findByTypeAndStudyId_returnsEmptyListForNonExistentType() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findByTypeAndStudyId("nonExistentType", 1);
        assertThat(trajectoryEntities).isEmpty();
    }

    @Test
    void findByTypeAndStudyId_returnsEmptyListForNonExistentStudyId() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), 999);
        assertThat(trajectoryEntities).isEmpty();
    }

    @Test
    void findByTypeAndStudyId_returnsListForNullType() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findByTypeAndStudyId("", 1);
        assertThat(trajectoryEntities.getFirst().getType()).isEqualTo("AREA");
    }

    @Test
    void findByTypeAndStudyId_returnsEmptyListForNullStudyId() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), null);
        assertThat(trajectoryEntities).isEmpty();
    }

    @Test
    void findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc_returnsTrajectoryEntity() {
        Optional<TrajectoryEntity> foundEntity = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                "testFile.txt", "AREA", "2023-2024", "FR", "technology1");

        assertThat(foundEntity).isPresent();
        assertThat(foundEntity.get().getFileName()).isEqualTo("testFile.txt");
        assertThat(foundEntity.get().getType()).isEqualTo("AREA");
        assertThat(foundEntity.get().getHorizon()).isEqualTo("2023-2024");
        assertThat(foundEntity.get().getArea()).isEqualTo("FR");
        assertThat(foundEntity.get().getTechnology()).isEqualTo("technology1");
    }

    @Test
    void findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc_returnsEmptyOptionalForNonExistentFile() {
        Optional<TrajectoryEntity> foundEntity = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(
                "nonExistentFile.txt", "AREA", "2023-2024", "FR", "technology1");

        assertThat(foundEntity).isNotPresent();
    }

}


