package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowbasedTypeDaysRepository extends JpaRepository<FlowbasedTypeDayEntity, Integer> {
    @Query("""
              SELECT t FROM FlowbasedTypeDayEntity t
              WHERE t.trajectory.id = :id
              ORDER BY t.idTypeDay ASC
           """)
    List<FlowbasedTypeDayEntity> findEntitiesByTrajectoryId(Integer id);
}
