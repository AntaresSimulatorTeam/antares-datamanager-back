package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DataJpaTest
class FlowbasedTypeDaysRepositoryTest {

    @Autowired
    private FlowbasedTypeDaysRepository repository;

    @PersistenceContext
    private EntityManager entityManager;
    
    private FlowbasedTypeDayEntity savedFlowbasedTypeDay;

    @BeforeEach
    void setUp() {

        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setType("FLOWBASED");
        trajectory.setFileName("porygon");
        trajectory.setChecksum("checkSum");
        trajectory.setCreatedBy("prygongon");
        trajectory.setFileSize(1234L);
        trajectory.setVersion(1);
        trajectory.setCreationDate(LocalDateTime.now());
        entityManager.persist(trajectory);

        FlowbasedTypeDayEntity flowbased = new FlowbasedTypeDayEntity();
        flowbased.setTrajectory(trajectory);
        flowbased.setIdTypeDay(1);
        flowbased.setClustering("winter1");

        FlowbasedTypeDayEntity flowbased2 = new FlowbasedTypeDayEntity();
        flowbased2.setTrajectory(trajectory);
        flowbased2.setIdTypeDay(2);
        flowbased2.setClustering("winter2");

        entityManager.persist(flowbased);
        entityManager.persist(flowbased2);

        entityManager.flush();

        savedFlowbasedTypeDay = entityManager.find(FlowbasedTypeDayEntity.class, flowbased.getId());
    }

    @Test
    void shouldFindEntitiesByTrajectoryIdOrderedByName() {
        // When
        List<FlowbasedTypeDayEntity> result =
                repository.findEntitiesByTrajectoryId(savedFlowbasedTypeDay.getTrajectory().getId());

        // Then
        assertThat(result)
                .hasSize(2)
                .extracting(FlowbasedTypeDayEntity::getIdTypeDay)
                .containsExactly(1, 2);
    }
}