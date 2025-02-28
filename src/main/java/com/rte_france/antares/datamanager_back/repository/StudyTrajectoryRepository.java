package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyTrajectoryRepository extends JpaRepository<StudyTrajectoryEntity, StudyTrajectoryKey> {
}
