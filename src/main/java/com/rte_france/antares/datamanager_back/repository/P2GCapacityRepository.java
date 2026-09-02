package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GCapacityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface P2GCapacityRepository extends JpaRepository<P2GCapacityEntity, Integer> {

    List<P2GCapacityEntity> findByTrajectoryId(Integer trajectoryId);
}
