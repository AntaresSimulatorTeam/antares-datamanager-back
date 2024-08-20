package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalCostTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThermalCostTypeRepository extends JpaRepository<ThermalCostTypeEntity, Integer> {

    Optional<ThermalCostTypeEntity> findThermalCostTypeEntityByFuelAndCountry(String fuel, String country);

}