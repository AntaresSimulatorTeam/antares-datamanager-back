package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoadRepository extends JpaRepository<LoadEntity, Integer> {
    boolean existsByFileName(String fileName);

    @Query("SELECT l FROM LoadEntity l JOIN l.trajectoryEntities t WHERE l.fileName = :fileName AND t.fileName = :trajectoryFileName")
    Optional<LoadEntity> findByFileNameAndTrajectoryFileName(@Param("fileName") String fileName, @Param("trajectoryFileName") String trajectoryFileName);

}
