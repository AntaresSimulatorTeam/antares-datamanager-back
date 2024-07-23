package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.Type;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;
import java.util.List;

public interface TrajectoryService {

     TrajectoryEntity processTrajectory(Type trajectoryType, String trajectoryToUse) throws IOException;

     List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameStartWithFromDB(Type trajectoryType, String fileNameStartsWith);

     List<String> findTrajectoriesByTypeAndFileNameStartWithFromFS(Type trajectoryType);


}
