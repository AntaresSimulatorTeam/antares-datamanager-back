package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.NuclearModulationParameterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NuclearModulationParameterRepository extends JpaRepository<NuclearModulationParameterEntity, Integer> {

    /**
     * Find all nuclear modulation parameters by trajectory ID
     * @param trajectoryId the trajectory ID
     * @return list of nuclear modulation parameters
     */
    List<NuclearModulationParameterEntity> findByTrajectoryId(Integer trajectoryId);

    /**
     * Find nuclear modulation parameters by trajectory ID and type
     * @param trajectoryId the trajectory ID
     * @param type the modulation type (nucFR_modul_hourly, nucFR_modul_daily, nucFR_modul_weekly)
     * @return list of nuclear modulation parameters
     */
    List<NuclearModulationParameterEntity> findByTrajectoryIdAndType(Integer trajectoryId, String type);
}

