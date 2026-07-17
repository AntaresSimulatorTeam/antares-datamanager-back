package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.SettingsSeedsParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettingsSeedsParametersRepository extends JpaRepository<SettingsSeedsParametersEntity, Integer> {
    Optional<SettingsSeedsParametersEntity> findByTrajectoryId(Integer trajectoryId);
}
