package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;
import java.util.List;

public interface TrajectoryService {

     TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse) throws IOException;

     List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameStartWithFromDB(TrajectoryType trajectoryType, String fileNameStartsWith);

     List<String> findTrajectoriesByTypeAndFileNameStartWithFromFS(TrajectoryType trajectoryType);


}
