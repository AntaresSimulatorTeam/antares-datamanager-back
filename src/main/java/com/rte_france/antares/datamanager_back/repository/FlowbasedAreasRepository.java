package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedAreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowbasedAreasRepository extends JpaRepository<FlowbasedAreaEntity, Integer> {

    @Query("""
            SELECT area.area FROM FlowbasedAreaEntity area
            WHERE area.trajectory.id = :trajectoryId
            ORDER BY area.id ASC
            """)
    List<String> findAreasByTrajectoryId(Integer trajectoryId);
}
