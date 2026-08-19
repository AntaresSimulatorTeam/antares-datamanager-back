package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlowbasedVirtualNodesRepository extends JpaRepository<FlowbasedVirtualNodesEntity, Integer> {
    Optional<FlowbasedVirtualNodesEntity> findByTrajectoryId(Integer trajectoryId);
}
