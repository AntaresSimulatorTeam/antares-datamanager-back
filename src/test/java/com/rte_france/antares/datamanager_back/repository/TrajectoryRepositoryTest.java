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
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains("nonExistentType", "2023-2024", "test","FR");
        assertThat(trajectoryEntities).isEmpty();
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartsWith_returnsEmptyListForNonExistentFileNameStartsWith() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains("AREA", "2023-2024", "nonExistentStart", "FR");
        assertThat(trajectoryEntities).isEmpty();
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartsWith_returnsNonEmptyListForExistentTypeAndFileNameStartsWith() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains("AREA", "2023-2024", "TEst", "FR");
        assertThat(trajectoryEntities).isNotEmpty();
        assertThat(trajectoryEntities.getFirst().getFileName()).startsWith("test");
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartsWith_returnsEmptyListForNullType() {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(null, "2023-2024", "test", "FR");
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
    void findMostRecentTrajectoriesByHorizon_shouldReturnMostRecentVersions() {
        // Exécution
        String horizon = "2023-2024"; // Utiliser l'horizon qui existe dans vos données de test
        List<TrajectoryEntity> result = trajectoryRepository.findMostRecentTrajectoriesByHorizon(horizon);

        // Vérifications
        assertThat(result).isNotEmpty();

        // Vérifier que chaque trajectoire a la version la plus récente
        result.forEach(trajectory -> {
            List<TrajectoryEntity> allVersions = trajectoryRepository.findAll().stream()
                    .filter(t -> t.getFileName().equals(trajectory.getFileName())
                            && t.getHorizon().equals(trajectory.getHorizon()))
                    .toList();

            int maxVersion = allVersions.stream()
                    .mapToInt(TrajectoryEntity::getVersion)
                    .max()
                    .orElse(0);

            assertThat(trajectory.getVersion()).isEqualTo(maxVersion);
        });

        // Vérifier que les résultats sont triés par date de création
        assertThat(result).isSortedAccordingTo(
                Comparator.comparing(TrajectoryEntity::getCreationDate).reversed()
        );
    }
}


