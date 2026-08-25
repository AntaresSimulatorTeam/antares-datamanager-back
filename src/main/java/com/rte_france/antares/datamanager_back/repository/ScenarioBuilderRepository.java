package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ScenarioBuilderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScenarioBuilderRepository extends JpaRepository<ScenarioBuilderEntity, Integer> {
    List<ScenarioBuilderEntity> findByTrajectoryId(Integer trajectoryId);
}
