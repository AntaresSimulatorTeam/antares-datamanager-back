package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterCapacityEntity;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.nio.file.Path;

public interface ThermalControlService {

      void checkMissingClusters(Integer studyId, String horizon, Set<String> paramClusters, TrajectoryType trajectoryType, String area);

      void verifyClustersInCommonParamTrajectory(Integer studyId, String horizon, List<ThermalClusterCapacityEntity> capacities);

      void verifyClustersInSpecificParamTrajectory(Integer studyId, String horizon, List<ThermalClusterCapacityEntity> capacities);

      void verifyCostsTrajectory(String horizon, Path trajectoryFilePath, String trajectoryName) throws IOException;
     }
