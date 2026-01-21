package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository

public interface AreaConfigRepository extends JpaRepository<AreaConfigEntity, Integer> {


        @Query("SELECT a.name, ac.spilledEnergyCost, ac.unsuppliedEnergyCost " +
                "FROM AreaConfigEntity ac " +
                "JOIN ac.area a " +
                "WHERE ac.trajectory.id = :trajectoryId")
        List<Object[]> findAreaConfigByTrajectoryId(@Param("trajectoryId") Integer trajectoryId);
    }



