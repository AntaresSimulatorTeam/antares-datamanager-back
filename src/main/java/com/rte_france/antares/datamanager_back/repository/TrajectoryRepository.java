package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrajectoryRepository extends JpaRepository<TrajectoryEntity, String> {

    Optional<TrajectoryEntity> findFirstByFileNameOrderByVersionDesc(String fileName);

}
