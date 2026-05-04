package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudyTrajectoryRepository extends JpaRepository<StudyTrajectoryEntity, StudyTrajectoryKey> {
  List<StudyTrajectoryEntity> findById_ScenarioId(Integer scenarioId);

  @Modifying
  @Query("DELETE FROM studyTrajectory e " +
          "WHERE e.id.scenarioId = :studyId AND e.id.trajectoryId IN :trajectoryIds")
  int deleteByStudyIdAndTrajectoryIds(@Param("studyId") Integer studyId, @Param("trajectoryIds") List<Integer> trajectoryIds);

  boolean existsById_TrajectoryId(Integer trajectoryId);
}
