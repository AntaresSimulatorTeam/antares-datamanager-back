package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrajectoryRepository extends JpaRepository<TrajectoryEntity, String> {

    Optional<TrajectoryEntity> findFirstByFileNameOrderByVersionDesc(String fileName);


    @Query("SELECT t FROM Trajectory t WHERE t.type = :type AND t.fileName LIKE CONCAT('%', CONCAT(:fileNameStartsWith, '%'))")
    List<TrajectoryEntity> findTrajectoriesFileNameByTypeAndFileNameStartsWith(@Param("type") String type, @Param("fileNameStartsWith") String fileNameStartsWith);

    Page<TrajectoryEntity> findAll(Specification<TrajectoryEntity> spec, Pageable pageable);
}

