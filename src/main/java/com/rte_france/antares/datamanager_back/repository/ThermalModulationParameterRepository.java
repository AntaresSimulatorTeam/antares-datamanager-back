package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalBaseEntity;
import com.rte_france.antares.datamanager_back.repository.model.ThermalModulationParameterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ThermalModulationParameterRepository extends JpaRepository<ThermalModulationParameterEntity, Integer> {

    @Query("SELECT l FROM ThermalModulationParameterEntity l JOIN l.trajectoryEntities t WHERE l.tsName = :fileName AND t.fileName = :trajectoryFileName")
    Optional< ThermalModulationParameterEntity> findByFileNameAndTrajectoryFileName(@Param("fileName") String fileName, @Param("trajectoryFileName") String trajectoryFileName);
}
