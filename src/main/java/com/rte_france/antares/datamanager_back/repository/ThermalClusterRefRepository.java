package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterRef;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ThermalClusterRefRepository extends JpaRepository<ThermalClusterRef, Integer> {

    @Query("SELECT tcr FROM ThermalClusterRef tcr " +
            "JOIN FETCH tcr.thermalTechnology tt " +
            "WHERE tcr.name = ?1 AND tt.name = :name AND tt.name = :technology")
    Optional<ThermalClusterRef> findByNameAndNameAndThermalTechnology(String name, String technology);

}
