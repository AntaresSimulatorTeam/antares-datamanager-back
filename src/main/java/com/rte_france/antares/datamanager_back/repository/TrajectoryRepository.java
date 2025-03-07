package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrajectoryRepository extends JpaRepository<TrajectoryEntity, Integer> {

    Optional<TrajectoryEntity> findTrajectoryEntityById(Integer id);

    @ExecutionTime
    Optional<TrajectoryEntity> findFirstByFileNameOrderByVersionDesc(String fileName);

    @Query("SELECT t " +
            "FROM Trajectory t " +
            "WHERE t.creationDate IN (" +
                "SELECT MAX(t1.creationDate) " +
                "FROM Trajectory t1 " +
                "WHERE t1.type = :type AND t1.horizon = :horizon AND (t1.fileName LIKE CONCAT('%', CONCAT(:fileNameStartsWith, '%')) OR :fileNameStartsWith IS NULL) " +
                "GROUP BY t1.fileName" +
            ") " +
            "ORDER BY t.creationDate DESC")
    List<TrajectoryEntity> findTrajectoriesFileNameByTypeAAndHorizonAndFileNameStartsWith(@Param("type") String type, @Param("horizon") String horizon, @Param("fileNameStartsWith") String fileNameStartsWith);

    @Query("SELECT t FROM Trajectory t JOIN t.scenarioEntities s WHERE t.type = :type AND s.id = :studyId")
    List<TrajectoryEntity> findByTypeAndStudyId(@Param("type") String type, @Param("studyId") Integer studyId);

}

