package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.HydroParametersMeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HydroParametersMeRepository extends JpaRepository<HydroParametersMeEntity, Long> {

    List<HydroParametersMeEntity> findByTrajectoryId(Integer trajectoryId);

    @Modifying
    @Query("DELETE FROM HydroParametersMeEntity h WHERE h.trajectoryId = :trajectoryId")
    void deleteByTrajectoryId(@Param("trajectoryId") Integer trajectoryId);

}
