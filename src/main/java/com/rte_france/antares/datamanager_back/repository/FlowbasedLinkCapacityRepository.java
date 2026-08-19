package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlowbasedLinkCapacityRepository extends JpaRepository<FlowbasedLinkCapacityEntity, Integer> {
    Optional<FlowbasedLinkCapacityEntity> findByTrajectoryId(Integer trajectoryId);
}
