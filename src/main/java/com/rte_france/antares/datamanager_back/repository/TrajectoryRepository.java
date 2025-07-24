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

    Optional<TrajectoryEntity> findFirstByFileNameAndHorizonAndLoadAreaOrderByVersionDesc(String fileName, String horizon, String loadArea);


    @Query("""
                 SELECT t
                 FROM Trajectory t
                 WHERE t.type = :type\s
                 AND t.horizon = :horizon
                 AND (:fileNameContains IS NULL OR LOWER(t.fileName) LIKE LOWER(CONCAT('%', :fileNameContains, '%')))
                 AND (:loadArea IS NULL OR TRIM(:loadArea) = '' OR t.loadArea = :loadArea)
                 AND t.version = (
                     SELECT MAX(t1.version)\s
                     FROM Trajectory t1\s
                     WHERE t1.fileName = t.fileName\s
                     AND t1.type = :type\s
                     AND t1.horizon = :horizon \s
                     AND (:loadArea IS NULL OR TRIM(:loadArea) = '' OR t1.loadArea = :loadArea) \s
                 )
                 ORDER BY t.creationDate DESC
            \s""")
    List<TrajectoryEntity> findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(@Param("type") String type, @Param("horizon") String horizon, @Param("fileNameContains") String fileNameContains, @Param("loadArea") String loadArea);

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

    // Dans TrajectoryRepository.java
    @Query("""
    SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END
    FROM Trajectory t
    JOIN t.loadEntities l
    JOIN t.scenarioEntities st
    WHERE t.type = 'LOAD'
      AND t.loadArea <> 'OTHERS'
      AND st.id = :studyId
      AND l.area = :loadArea
""")
    boolean existsOtherLoadTrajectoryLinkedToStudyAndLoad(@Param("studyId") Integer studyId,
                                                          @Param("loadArea") String loadArea);

}

