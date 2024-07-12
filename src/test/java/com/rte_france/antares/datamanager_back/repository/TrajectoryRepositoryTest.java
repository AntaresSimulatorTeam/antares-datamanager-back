package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TrajectoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TrajectoryRepository trajectoryRepository;

    @Test
    void findFirstByFileNameOrderByVersionDesc_returnsTrajectoryEntity() {
        TrajectoryEntity trajectoryEntity = new TrajectoryEntity();
        trajectoryEntity.setFileName("testFile.txt");
        entityManager.persistAndFlush(trajectoryEntity);

        Optional<TrajectoryEntity> foundEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc("testFile.txt");

        assertThat(foundEntity).isPresent();
        assertThat(foundEntity.get().getFileName()).isEqualTo("testFile.txt");
    }

    @Test
    void findFirstByFileNameOrderByVersionDesc_returnsEmptyOptionalForNonExistentFile() {
        Optional<TrajectoryEntity> foundEntity = trajectoryRepository.findFirstByFileNameOrderByVersionDesc("nonExistentFile.txt");

        assertThat(foundEntity).isNotPresent();
    }
}
