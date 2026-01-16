package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThermalClusterRefRepository extends JpaRepository<ThermalClusterRef, Integer> {

    Optional<ThermalClusterRef> findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase(
            String technology,
            String name
    );

    Optional<ThermalClusterRef> findByThermalTechnologyIsNullAndNameIgnoreCase(String name);


}
