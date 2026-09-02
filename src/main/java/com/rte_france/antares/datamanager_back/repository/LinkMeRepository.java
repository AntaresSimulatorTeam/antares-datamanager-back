package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.LinkMeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkMeRepository extends JpaRepository<LinkMeEntity, Integer> {

    List<LinkMeEntity> findLinkMeEntitiesByTrajectoryIdIs(Integer trajectoryId);
}
