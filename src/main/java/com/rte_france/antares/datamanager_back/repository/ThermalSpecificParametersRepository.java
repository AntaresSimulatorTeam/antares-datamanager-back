package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalSpecificParametersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThermalSpecificParametersRepository extends JpaRepository<ThermalSpecificParametersEntity, Integer> {
//TODO Fix it
    @Query("""
            select p from ThermalSpecificParametersEntity p JOIN p.trajectory.scenarioEntities s
            where p.trajectory.horizon = :horizon AND s.id =:studyId
              and (
                coalesce(upper(p.trajectory.area), '') <> 'OTHERS'
                or not exists (
                  select 1
                  from ThermalSpecificParametersEntity p2
                 JOIN p2.trajectory.scenarioEntities s
            where p2.trajectory.horizon = :horizon AND s.id =:studyId
                    and coalesce(upper(p2.area),'') = coalesce(upper(p.area),'')
                    and coalesce(upper(p2.cluster),'') = coalesce(upper(p.cluster),'')
                    and coalesce(upper(p2.trajectory.area),'') <> 'OTHERS'
                )
              )
            """)
    List<ThermalSpecificParametersEntity> findPreferredEntitiesByStudyIdAndHorizon(
            @Param("studyId") Integer studyId,
            @Param("horizon") String horizon);

}
