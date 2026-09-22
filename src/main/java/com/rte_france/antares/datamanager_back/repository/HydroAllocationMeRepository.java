package com.rte_france.antares.datamanager_back.repository;

import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationMeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HydroAllocationMeRepository extends JpaRepository<HydroAllocationMeEntity, Long> {

    List<HydroAllocationMeEntity> findByTrajectoryId(Integer trajectoryId);

    @Modifying
    @Query("DELETE FROM HydroAllocationMeEntity h WHERE h.trajectoryId = :trajectoryId")
    void deleteByTrajectoryId(@Param("trajectoryId") Integer trajectoryId);

}
