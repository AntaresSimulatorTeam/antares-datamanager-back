package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HydroAllocationRepository extends JpaRepository<HydroAllocationEntity, Integer> {
    @Query("""
              SELECT t FROM HydroAllocation t
              WHERE t.trajectory.id = :id
              ORDER BY t.hydro ASC
           """)
    List<HydroAllocationEntity> findHydroAllocationEntitiesByTrajectoryId(Integer id);
}
