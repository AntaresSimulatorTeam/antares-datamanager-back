package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlowbasedTypeDaysRepository extends JpaRepository<FlowbasedTypeDayEntity, Integer> {
    Optional<FlowbasedTypeDayEntity> findByTrajectoryId(Integer trajectoryId);
}
