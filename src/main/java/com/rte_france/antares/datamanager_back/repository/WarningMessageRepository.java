package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.WarningMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WarningMessageRepository extends JpaRepository<WarningMessageEntity, Integer> {

        @Query("SELECT CASE WHEN COUNT(w) > 0 THEN TRUE ELSE FALSE END " +
                "FROM WarningMessageEntity w " +
                "LEFT OUTER JOIN w.trajectory t " +
                "LEFT OUTER JOIN w.study s " +
                "WHERE w.warningContent = :warningContent " +
                "AND t.id = :trajectoryId " +
                "AND s.id = :studyId")
        boolean existsByWarningContentAndTrajectoryIdAndStudyId(@Param("warningContent") String warningContent,
                                                                @Param("trajectoryId") Integer trajectoryId,
                                                                @Param("studyId") Integer studyId);


        @Query("SELECT w FROM WarningMessageEntity w WHERE w.study.id = :studyId AND w.trajectory.id = :trajectoryId")
        List<WarningMessageEntity> findByStudyIdAndTrajectoryId(@Param("studyId") Integer studyId, @Param("trajectoryId") Integer trajectoryId);
}
