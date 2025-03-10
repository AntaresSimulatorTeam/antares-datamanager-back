package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;
import java.util.List;

public interface TrajectoryService {

    TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse, String horizon) throws IOException;

    List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameContainsFromDB(TrajectoryType trajectoryType, String horizon, String fileNameStartsWith);

    List<FsTrajectoryDTO> findTrajectoriesByType(TrajectoryType trajectoryType);

    List<TrajectoryDTO> findTrajectoriesByTypeAndStudyId(String trajectoryType, Integer studyId);

    TrajectoryEntity linkTrajectoryToStudy(Integer trajectoryId, Integer studyId, TrajectoryType type);

    void unlinkTrajectoryFromStudy(Integer trajectoryId, Integer studyId);

}
