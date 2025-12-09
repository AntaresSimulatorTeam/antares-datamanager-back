package com.rte_france.antares.datamanager_back.service.thermal;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.ThermalSpecificParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ThermalSpecificFileProcessorService {

    List<ThermalSpecificParametersEntity> buildThermalSpecificParameterValueList(String trajectoryName, Path trajectoryFilePath, String horizon, String area, Integer studyId);


    TrajectoryEntity saveThermalSpecificTrajectory(TrajectoryEntity trajectory, List<ThermalSpecificParametersEntity> thermalSpecificParameters, TrajectoryType type);

    Set<String> getListClusterByAreaForSpecificParam(String horizon, Integer studyId, boolean mr);

    boolean isParamModulationRequired(String horizon, Integer studyId);
}
