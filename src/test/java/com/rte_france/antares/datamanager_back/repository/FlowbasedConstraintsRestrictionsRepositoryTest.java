package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedConstraintsRestrictionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FlowbasedConstraintsRestrictionsRepositoryTest {

    @Autowired
    private FlowbasedConstraintsRestrictionsRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldFindRestrictionsByTrajectory() {
        TrajectoryEntity trajectory = persistTrajectory("constraints-trajectory");
        TrajectoryEntity otherTrajectory = persistTrajectory("other-constraints-trajectory");

        persistRestriction(trajectory, "restriction-1");
        persistRestriction(trajectory, "restriction-2");
        persistRestriction(otherTrajectory, "other-restriction");
        entityManager.flush();

        List<FlowbasedConstraintsRestrictionEntity> result =
                repository.findEntitiesByTrajectoryId(trajectory.getId());

        assertThat(result)
                .hasSize(2)
                .extracting(FlowbasedConstraintsRestrictionEntity::getName)
                .containsExactlyInAnyOrder("restriction-1", "restriction-2");
    }

    private TrajectoryEntity persistTrajectory(String fileName) {
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setType("FLOWBASED");
        trajectory.setFileName(fileName);
        trajectory.setChecksum(fileName + "-checksum");
        trajectory.setCreatedBy("test");
        trajectory.setFileSize(1L);
        trajectory.setVersion(1);
        entityManager.persist(trajectory);
        return trajectory;
    }

    private void persistRestriction(TrajectoryEntity trajectory, String name) {
        FlowbasedConstraintsRestrictionEntity restriction = new FlowbasedConstraintsRestrictionEntity();
        restriction.setTrajectory(trajectory);
        restriction.setName(name);
        restriction.setEnabled(true);
        entityManager.persist(restriction);
    }
}
