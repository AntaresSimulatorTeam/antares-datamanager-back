package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.HydroCapacityMeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HydroCapacityMeRepository extends JpaRepository<HydroCapacityMeEntity, Integer> {
    
    @Query("SELECT DISTINCT h.node FROM HydroCapacityMe h " +
           "WHERE h.trajectory.id IN (" +
           "  SELECT t.id FROM Trajectory t " +
           "  WHERE t.type = 'HYDRO_CAPACITY_ME' " +
           ")")
    List<String> findAllDistinctNodes();

    @Query("SELECT DISTINCT h.node FROM HydroCapacityMe h " +
           "WHERE h.trajectory.id IN (" +
           "  SELECT st.trajectory.id FROM studyTrajectory st " +
           "  WHERE st.id.scenarioId = :studyId AND st.trajectory.type = 'HYDRO_CAPACITY_ME'" +
           ")")
    List<String> findDistinctNodesByStudyId(@Param("studyId") Integer studyId);
}

