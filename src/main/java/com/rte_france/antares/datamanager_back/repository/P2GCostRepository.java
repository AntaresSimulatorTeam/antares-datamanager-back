package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GCostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface P2GCostRepository extends JpaRepository<P2GCostEntity, Integer> {

    List<P2GCostEntity> findByTrajectoryId(Integer trajectoryId);
}
