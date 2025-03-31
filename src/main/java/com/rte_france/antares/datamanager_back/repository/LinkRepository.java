package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.LinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkRepository extends JpaRepository<LinkEntity, String> {

   List<LinkEntity> findLinkEntitiesByTrajectoryIdIs(Integer trajectory_id);
}
