package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
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
class FlowbasedLinkCapacityRepositoryTest {

    @Autowired
    private FlowbasedLinkCapacityRepository repository;

    @PersistenceContext
    private EntityManager entityManager;
    
    private FlowbasedLinkCapacityEntity savedFlowbasedLink;

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

        FlowbasedLinkCapacityEntity flowbased = new FlowbasedLinkCapacityEntity();
        flowbased.setTrajectory(trajectory);
        flowbased.setName("Z_LINK");
        flowbased.setHurdlesCost(false);

        FlowbasedLinkCapacityEntity flowbased2 = new FlowbasedLinkCapacityEntity();
        flowbased2.setTrajectory(trajectory);
        flowbased2.setName("A_LINK");
        flowbased2.setHurdlesCost(false);

        entityManager.persist(flowbased);
        entityManager.persist(flowbased2);

        entityManager.flush();

        savedFlowbasedLink = entityManager.find(FlowbasedLinkCapacityEntity.class, flowbased.getId());
    }

    @Test
    void shouldFindEntitiesByTrajectoryIdOrderedByName() {
        // When
        List<FlowbasedLinkCapacityEntity> result =
                repository.findEntitiesByTrajectoryId(savedFlowbasedLink.getTrajectory().getId());

        // Then
        assertThat(result)
                .hasSize(2)
                .extracting(FlowbasedLinkCapacityEntity::getName)
                .containsExactly("A_LINK", "Z_LINK");
    }
}