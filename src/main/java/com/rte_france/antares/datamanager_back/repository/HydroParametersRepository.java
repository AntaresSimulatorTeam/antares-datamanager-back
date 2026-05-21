package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HydroParametersRepository extends JpaRepository<HydroParametersEntity, Integer> {
    @Query("""
              SELECT t FROM HydroParameters t
              WHERE t.trajectory.id = :id
              ORDER BY t.node ASC
           """)
    List<HydroParametersEntity> findHydroParametersEntitiesByTrajectoryId(Integer id);
}
