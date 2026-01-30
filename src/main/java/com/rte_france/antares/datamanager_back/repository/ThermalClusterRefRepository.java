package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThermalClusterRefRepository extends JpaRepository<ThermalClusterRef, Integer> {

    Optional<ThermalClusterRef> findByNameIgnoreCaseAndNamePemmdbIgnoreCaseAndThermalTechnology(
            String trimmedName,
            String normalizedPemmdb,
            ThermalTechnology tech
    );


}
