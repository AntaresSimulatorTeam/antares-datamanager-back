package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkListEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FlowbasedLinkListRepositoryTest {

    @Autowired
    private FlowbasedLinkListRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldFindListEntriesByTrajectoryAndListNameInPositionOrder() {
        TrajectoryEntity trajectory = persistTrajectory("links-trajectory");
        TrajectoryEntity otherTrajectory = persistTrajectory("other-links-trajectory");
        persistLink(trajectory, "listLinks1", "ch-fr", 2);
        persistLink(trajectory, "listLinks1", "fr-itn", 1);
        persistLink(trajectory, "listLinks1", "ch-fr", 3);
        persistLink(trajectory, "listLinks2", "not-in-result", 1);
        persistLink(otherTrajectory, "listLinks1", "other-trajectory", 1);
        entityManager.flush();

        List<FlowbasedLinkListEntity> result =
                repository.findEntitiesByTrajectoryIdAndListName(trajectory.getId(), "listLinks1");

        assertThat(result)
                .hasSize(3)
                .extracting(FlowbasedLinkListEntity::getLink)
                .containsExactly("fr-itn", "ch-fr", "ch-fr");
        assertThat(result)
                .extracting(FlowbasedLinkListEntity::getPosition)
                .containsExactly(1, 2, 3);
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

    private void persistLink(TrajectoryEntity trajectory, String listName, String link, int position) {
        FlowbasedLinkListEntity entry = new FlowbasedLinkListEntity();
        entry.setTrajectory(trajectory);
        entry.setListName(listName);
        entry.setLink(link);
        entry.setPosition(position);
        entityManager.persist(entry);
    }
}
