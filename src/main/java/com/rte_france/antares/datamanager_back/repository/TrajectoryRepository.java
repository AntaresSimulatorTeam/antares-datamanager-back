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
    Optional<TrajectoryEntity> findFirstByFileNameAndHorizonOrderByVersionDesc(String fileName, String horizon);


    @Query("""
                SELECT t
                FROM Trajectory t
                WHERE t.type = :type\s
                AND t.horizon = :horizon
                AND (:fileNameContains IS NULL OR LOWER(t.fileName) LIKE LOWER(CONCAT('%', :fileNameContains, '%')))
                AND t.version = (
                    SELECT MAX(t1.version)\s
                    FROM Trajectory t1\s
                    WHERE t1.fileName = t.fileName\s
                    AND t1.type = :type\s
                    AND t1.horizon = :horizon
                )
                ORDER BY t.creationDate DESC
           \s""")
    List<TrajectoryEntity> findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(@Param("type") String type, @Param("horizon") String horizon, @Param("fileNameContains") String fileNameContains);

    @Query("SELECT t FROM Trajectory t JOIN t.scenarioEntities s WHERE (:type IS NULL OR :type = '' OR t.type = :type) AND s.id = :studyId")
    List<TrajectoryEntity> findByTypeAndStudyId(@Param("type") String type, @Param("studyId") Integer studyId);

}

