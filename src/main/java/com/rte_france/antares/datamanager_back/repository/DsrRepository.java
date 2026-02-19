package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.DsrClusterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DsrRepository extends JpaRepository<DsrClusterEntity, Integer> {

    @Query("""
                SELECT  d from DsrCluster d JOIN d.trajectory.scenarioEntities s
            where  s.id =:studyId
            """)
    List<DsrClusterEntity> findAllDsrClusterEntitiesByStudyId(@Param("studyId") Integer studyId);
}