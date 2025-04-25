package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AreaRepository extends JpaRepository<AreaEntity, String> {

    Optional<AreaEntity> findAreaByName(String name);

    @Query("SELECT a FROM Area a " +
            "LEFT OUTER JOIN AreaConfigEntity ac ON ac.area.id = a.id " +
            "LEFT OUTER JOIN Trajectory t ON t.id = ac.trajectory.id " +
            "LEFT OUTER JOIN studyTrajectory st ON st.trajectory.id = t.id " +
            "WHERE st.studyEntity.id = :studyId AND a.name = :areaName")
    Optional<AreaEntity> findAreaByNameAndStudyId(@Param("areaName") String areaName, @Param("studyId") Integer studyId);
}
