package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.SettingsOptimizationParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettingsOptimizationParametersRepository extends JpaRepository<SettingsOptimizationParametersEntity, Integer> {
    Optional<SettingsOptimizationParametersEntity> findByTrajectoryId(Integer trajectoryId);
}
