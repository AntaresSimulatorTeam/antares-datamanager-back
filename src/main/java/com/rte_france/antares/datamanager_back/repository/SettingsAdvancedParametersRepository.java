package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.SettingsAdvancedParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettingsAdvancedParametersRepository extends JpaRepository<SettingsAdvancedParametersEntity, Integer> {
    Optional<SettingsAdvancedParametersEntity> findByTrajectoryId(Integer trajectoryId);
}
