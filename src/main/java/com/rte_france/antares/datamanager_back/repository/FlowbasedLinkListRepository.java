package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowbasedLinkListRepository extends JpaRepository<FlowbasedLinkListEntity, Integer> {

    @Query("""
            SELECT linkList FROM FlowbasedLinkListEntity linkList
            WHERE linkList.trajectory.id = :trajectoryId
              AND linkList.listName = :listName
            """)
    List<FlowbasedLinkListEntity> findEntitiesByTrajectoryIdAndListName(
            @Param("trajectoryId") Integer trajectoryId, @Param("listName") String listName);
}
