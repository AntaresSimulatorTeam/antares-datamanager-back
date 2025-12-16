package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryKey;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThermalTechnologyRepository extends JpaRepository<ThermalTechnology, StudyTrajectoryKey>  {

    Optional<ThermalTechnology> findThermalTechnologyByNameIgnoreCase(String name);
}
