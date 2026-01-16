package com.rte_france.antares.datamanager_back.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(SpringExtension.class)
@DataJpaTest
class AreaConfigRepositoryTest {

    @Autowired
    private AreaConfigRepository areaConfigRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private AreaConfigEntity savedAreaConfig;

    @BeforeEach
    void setUp() {

        AreaEntity area = new AreaEntity();
        area.setName("TestArea");
        entityManager.persist(area);
        entityManager.flush();


        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setFileName("test_file");
        trajectory.setChecksum("checkSum");
        trajectory.setCreatedBy("MariaRojas");
        trajectory.setFileSize(1234L);
        trajectory.setType("AREA");
        trajectory.setVersion(1);
        trajectory.setCreationDate(LocalDateTime.now());
        entityManager.persist(trajectory);
        entityManager.flush();


        trajectory = entityManager.find(TrajectoryEntity.class, trajectory.getId());

        AreaConfigEntity areaConfig = new AreaConfigEntity();
        areaConfig.setArea(area);
        areaConfig.setTrajectory(trajectory);
        entityManager.persist(areaConfig);

        entityManager.flush();

        savedAreaConfig = entityManager.find(AreaConfigEntity.class, areaConfig.getId());
    }


    @Test
    void testFindAreaConfigByTrajectoryId() {
        List<Object[]> result = areaConfigRepository.findAreaConfigByTrajectoryId(savedAreaConfig.getTrajectory().getId());

        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).hasSize(3);
        assertThat(result.get(0)[0]).isEqualTo("TestArea");
        assertThat(result.get(0)[1]).isEqualTo(true);
        assertThat(result.get(0)[2]).isEqualTo(false);
    }
}
