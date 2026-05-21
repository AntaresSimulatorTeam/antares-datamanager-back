package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.HydroSeriesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HydroSeriesRepository extends JpaRepository<HydroSeriesEntity, Integer> {
    @Query("""
              SELECT t FROM HydroSeries t
              WHERE t.trajectory.id = :id
              ORDER BY t.tsName ASC
           """)
    List<HydroSeriesEntity> findHydroSeriesEntitiesByTrajectoryId(Integer id);
}
