package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
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
class FlowbasedVirtualNodesRepositoryTest {

    @Autowired
    private FlowbasedVirtualNodesRepository repository;

    @PersistenceContext
    private EntityManager entityManager;
    
    private FlowbasedVirtualNodesEntity savedFlowbasedTypeDay;

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

        FlowbasedVirtualNodesEntity flowbased = new FlowbasedVirtualNodesEntity();
        flowbased.setTrajectory(trajectory);
        flowbased.setName("alegro2");

        FlowbasedVirtualNodesEntity flowbased2 = new FlowbasedVirtualNodesEntity();
        flowbased2.setTrajectory(trajectory);
        flowbased2.setName("alegro1");

        entityManager.persist(flowbased);
        entityManager.persist(flowbased2);

        entityManager.flush();

        savedFlowbasedTypeDay = entityManager.find(FlowbasedVirtualNodesEntity.class, flowbased.getId());
    }

    @Test
    void shouldFindEntitiesByTrajectoryIdOrderedByName() {
        // When
        List<FlowbasedVirtualNodesEntity> result =
                repository.findEntitiesByTrajectoryId(savedFlowbasedTypeDay.getTrajectory().getId());

        // Then
        assertThat(result)
                .hasSize(2)
                .extracting(FlowbasedVirtualNodesEntity::getName)
                .containsExactly("alegro1", "alegro2");
    }
}