package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyTrajectoryKey;
import com.rte_france.antares.datamanager_back.repository.model.ThermalTechnology;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThermalTechnologyRepository extends JpaRepository<ThermalTechnology, StudyTrajectoryKey>  {

}
