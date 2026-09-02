package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.p2g.P2GParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface P2GParametersRepository extends JpaRepository<P2GParametersEntity, Integer> {

    List<P2GParametersEntity> findByTrajectoryId(Integer trajectoryId);
}
