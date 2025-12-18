package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.ThermalClusterCapacityEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ThermalControlService {

    void checkMissingClusters(Integer studyId, String horizon, Set<String> paramClusters, TrajectoryType trajectoryType, String area);

    void verifyClustersInCommonParamTrajectory(Integer studyId, String horizon, List<ThermalClusterCapacityEntity> capacities);

    void verifyClustersInSpecificParamTrajectory(Integer studyId, String horizon, List<ThermalClusterCapacityEntity> capacities);

    void verifyCostsTrajectory(String horizon, Path trajectoryFilePath, String trajectoryName, Integer studyId) throws IOException;

    void verifyThermalFuel(Integer studyId, String horizon, String trajectoryName, Set<String> listTechnology, TrajectoryType trajectoryType) throws IOException;
}
