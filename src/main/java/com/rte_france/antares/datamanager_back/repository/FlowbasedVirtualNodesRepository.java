package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowbasedVirtualNodesRepository extends JpaRepository<FlowbasedVirtualNodesEntity, Integer> {
    @Query("""
              SELECT t FROM FlowbasedVirtualNodesEntity t
              WHERE t.trajectory.id = :id
              ORDER BY t.name ASC
           """)
    List<FlowbasedVirtualNodesEntity> findEntitiesByTrajectoryId(Integer id);
}
