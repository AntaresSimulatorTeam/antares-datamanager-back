package com.rte_france.antares.datamanager_back.service.common;

import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDataDTO;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface TrajectoryService {

    TrajectoryEntity processLoadTrajectory(String area, String trajectoryToUse, String horizon, Integer studyId) throws IOException;

    TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse, String horizon, Integer studyId) throws IOException;

    TrajectoryEntity processThermalCapacityTrajectory(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String area, String technology) throws IOException;

    TrajectoryEntity processThermalCommonParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException;

    List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameContainsFromDB(TrajectoryType trajectoryType, String horizon, String fileNameContains, String area, String technology);

    List<FsTrajectoryDTO> findTrajectoriesByType(TrajectoryType trajectoryType, String area, String fileNameContains) throws TechnicalException;

    List<TrajectoryDTO> findTrajectoriesByTypeAndStudyId(String trajectoryType, Integer studyId);

    TrajectoryEntity linkTrajectoryToStudy(Integer trajectoryId, Integer studyId, TrajectoryType type) throws IOException;

    void unlinkTrajectoryFromStudy(Integer trajectoryId, Integer studyId);

    void unlinkBatchTrajectoriesFromStudy(Integer studyId, List<Integer> trajectoryIds);

    void unlinkAllTrajectoriesFromStudy(Integer studyId);

    List<TrajectoryDataDTO> getTrajectoryDataByTypeAndId(TrajectoryType trajectoryType, Integer trajectoryId);

    Map<String, Integer> countWarningMessage(Integer studyId);

    TrajectoryEntity processThermalSpecificParameterTrajectory(String trajectoryToUse, String horizon, String area, Integer studyId) throws IOException;

    TrajectoryEntity processThermalModulationParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException;

    TrajectoryEntity processThermalEconomicCostTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException;

    TrajectoryEntity processThermalEconomicParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException;
}

