package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StStorageRepository  extends JpaRepository<StStorageEntity, Integer> {
    @Query("""
              SELECT t FROM StStorage t
              WHERE t.trajectory.id = :id AND t.series = true
              ORDER BY t.area ASC
           """)
    List<StStorageEntity> findStStorageEntitiesByTrajectoryId(Integer id);
}
