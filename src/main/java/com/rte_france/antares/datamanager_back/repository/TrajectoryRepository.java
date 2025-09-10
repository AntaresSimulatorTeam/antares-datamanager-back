package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TrajectoryRepository extends JpaRepository<TrajectoryEntity, Integer> {

    @Query("SELECT DISTINCT t FROM Trajectory t " +
            "LEFT JOIN FETCH t.warningMessages " +
            "LEFT JOIN FETCH t.scenarioEntities " +
            "WHERE t.id IN :ids")
    Set<TrajectoryEntity> findAllByIdWithWarnings(@Param("ids") List<Integer> ids);

    @ExecutionTime
    Optional<TrajectoryEntity> findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(String fileName, String horizon, String type);

    Optional<TrajectoryEntity> findFirstByFileNameAndHorizonAndAreaOrderByVersionDesc(String fileName, String horizon, String area);

    Optional<TrajectoryEntity> findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyOrderByVersionDesc(String fileName, String type, String horizon, String area, String technology);


    @Query("""
                SELECT t
                FROM Trajectory t
                WHERE t.type = :type
                  AND t.horizon = :horizon
                  AND (:fileNameContains IS NULL OR t.fileName ILIKE CONCAT('%', CAST(:fileNameContains AS string), '%'))
                  AND (:area IS NULL OR TRIM(:area) = '' OR t.area = :area)
                  AND (
                      (:technology IS NULL AND t.technology IS NULL)
                      OR (:technology IS NOT NULL AND t.technology = :technology)
                  )
                  AND t.version = (
                      SELECT MAX(t1.version)
                      FROM Trajectory t1
                      WHERE t1.fileName = t.fileName
                        AND t1.type = :type
                        AND t1.horizon = :horizon
                        AND (:area IS NULL OR TRIM(:area) = '' OR t1.area = :area)
                        AND (
                            (:technology IS NULL AND t1.technology IS NULL)
                            OR (:technology IS NOT NULL AND t1.technology = :technology)
                        )
                  )
                ORDER BY t.creationDate DESC
            """)
    List<TrajectoryEntity> findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(@Param("type") String type,
                                                                                       @Param("horizon") String horizon,
                                                                                       @Param("fileNameContains") String fileNameContains,
                                                                                       @Param("area") String area,
                                                                                       @Param("technology") String technology);

    @Query("SELECT t FROM Trajectory t JOIN t.scenarioEntities s WHERE (:type IS NULL OR :type = '' OR t.type = :type) AND s.id = :studyId")
    List<TrajectoryEntity> findByTypeAndStudyId(@Param("type") String type, @Param("studyId") Integer studyId);


    @Query("""
                SELECT t FROM Trajectory t
                WHERE t.fileName IN (
                    SELECT DISTINCT t2.fileName
                    FROM Trajectory t2
                    JOIN t2.scenarioEntities s
                    WHERE s.id = :studyId
                )
                AND t.horizon = :targetHorizon
                AND t.version = (
                    SELECT MAX(t3.version)
                    FROM Trajectory t3
                    WHERE t3.fileName = t.fileName
                    AND t3.horizon = t.horizon
                )
                ORDER BY t.creationDate DESC
            """)
    List<TrajectoryEntity> findMostRecentTrajectoriesForDuplicationByStudyId(
            @Param("studyId") Integer studyId,
            @Param("targetHorizon") String targetHorizon
    );

}

