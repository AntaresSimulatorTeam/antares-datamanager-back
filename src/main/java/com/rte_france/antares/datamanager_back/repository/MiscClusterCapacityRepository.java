package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MiscClusterCapacityRepository extends JpaRepository<MiscClusterCapacityEntity, Integer> {

    @Query("""
    SELECT DISTINCT m.groupe AS groupe,
           m.area AS area,
              m.cluster AS cluster
    FROM MiscClusterCapacity m
    JOIN m.trajectory t
    JOIN t.scenarioEntities s
    WHERE s.id = :studyId
""")
    List<GroupAreaMiscCapacity> findByStudyId(Integer studyId);
}
