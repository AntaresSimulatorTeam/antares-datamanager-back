package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyTrajectoryRepository extends JpaRepository<StudyTrajectoryEntity, StudyTrajectoryKey> {
  List<StudyTrajectoryEntity> findById_ScenarioId(Integer scenarioId);
}
