package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThermalClusterRefRepository extends JpaRepository<ThermalClusterRef, Integer> {

    Optional<ThermalClusterRef> findByThermalTechnology_NameIgnoreCaseAndNameIgnoreCase(
            String technology,
            String name
    );

    List<ThermalClusterRef> findByThermalTechnologyIsNullAndNameIgnoreCase(String name);

    List<ThermalClusterRef> findByNamePemmdbIgnoreCase(String namePemmdb);

    List<ThermalClusterRef> findByNameIgnoreCase(String name);


}
