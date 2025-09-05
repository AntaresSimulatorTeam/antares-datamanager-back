package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalGroupMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThermalGroupMappingRepository extends JpaRepository<ThermalGroupMappingEntity, Integer> {
  Optional<ThermalGroupMappingEntity> findBySourceValueIgnoreCase(String sourceValue);
}
