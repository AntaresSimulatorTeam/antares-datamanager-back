package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedConstraintsRestrictionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowbasedConstraintsRestrictionsRepository
        extends JpaRepository<FlowbasedConstraintsRestrictionEntity, Integer> {

    @Query("""
            SELECT restriction FROM FlowbasedConstraintsRestrictionEntity restriction
            WHERE restriction.trajectory.id = :trajectoryId
            """)
    List<FlowbasedConstraintsRestrictionEntity> findEntitiesByTrajectoryId(Integer trajectoryId);
}
