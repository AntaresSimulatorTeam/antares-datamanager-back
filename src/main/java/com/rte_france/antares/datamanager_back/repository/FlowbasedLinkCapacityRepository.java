package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowbasedLinkCapacityRepository extends JpaRepository<FlowbasedLinkCapacityEntity, Integer> {
    @Query("""
              SELECT t FROM FlowbasedLinkCapacityEntity t
              WHERE t.trajectory.id = :id
              ORDER BY t.name ASC
           """)
    List<FlowbasedLinkCapacityEntity> findEntitiesByTrajectoryId(Integer id);
}
