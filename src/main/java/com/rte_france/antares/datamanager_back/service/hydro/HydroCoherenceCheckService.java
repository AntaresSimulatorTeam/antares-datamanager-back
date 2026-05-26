package com.rte_france.antares.datamanager_back.service.hydro;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.util.List;

public interface HydroCoherenceCheckService {
    void checkHydroSeriesTrajectoriesConsistency(Integer studyId, List<String> filesName, String areaParam, String trajectoryToUse) throws BusinessException;

    void checkHydroTPTrajectoriesConsistency(Integer studyId, List<String> areasTPFiles, String areaParam, String trajectoryToUse, String trajectoryType) throws BusinessException;

    List<String> getAreasInHydroSeriesModFiles(Integer trajectoryId);

    List<String> getAreasInHydroAllocationAreas(Integer trajectoryId);

    List<String> getAreasInHydroParametersAreas(Integer trajectoryId);

    void validateHydroSeriesCoherence(Integer studyId, TrajectoryEntity trajectory);

    void validateHydroTechnicalParametersCoherence(Integer studyId, TrajectoryEntity trajectory, TrajectoryType hydroType);
}
