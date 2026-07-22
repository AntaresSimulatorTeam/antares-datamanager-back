package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.SettingsGeneralParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettingsGeneralParametersRepository extends JpaRepository<SettingsGeneralParametersEntity, Integer> {
    Optional<SettingsGeneralParametersEntity> findByTrajectoryId(Integer trajectoryId);
}
