package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalSpecificParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThermalSpecificParametersRepository extends JpaRepository<ThermalSpecificParametersEntity, Integer> {

    @Query("SELECT t FROM ThermalSpecificParametersEntity t JOIN t.trajectory.scenarioEntities s WHERE t.trajectory.horizon = :horizon AND s.id =:studyId AND t.mrSpecific = 1")
    List<ThermalSpecificParametersEntity> findWithMrModulationByStudyIdAndHorizon(@Param("studyId") Integer studyId, @Param("horizon") String horizon);

    @Query("SELECT t FROM ThermalSpecificParametersEntity t JOIN t.trajectory.scenarioEntities s WHERE t.trajectory.horizon = :horizon AND s.id =:studyId AND t.cmSpecific = 1")
    List<ThermalSpecificParametersEntity> findWithCmModulationByStudyIdAndHorizon(@Param("studyId") Integer studyId, @Param("horizon") String horizon);

}
