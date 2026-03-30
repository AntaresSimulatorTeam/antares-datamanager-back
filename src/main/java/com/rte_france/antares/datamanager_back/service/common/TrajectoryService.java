package com.rte_france.antares.datamanager_back.service.common;

import com.rte_france.antares.datamanager_back.dto.*;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface TrajectoryService {

    TrajectoryEntity processLoadTrajectory(String area, String trajectoryToUse, String horizon, Integer studyId) throws IOException;

    TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse, String horizon, Integer studyId) throws Exception;

    TrajectoryEntity processThermalCapacityTrajectory(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String area, String technology) throws Exception;

    TrajectoryEntity processThermalCommonParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws Exception;

    List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameContainsFromDB(TrajectoryType trajectoryType, String horizon, String fileNameContains, String area, String technology);

    List<FsTrajectoryDTO> findTrajectoriesByType(TrajectoryType trajectoryType, String area, String technology, String fileNameContains) throws TechnicalException, IOException;

    List<TrajectoryDTO> findTrajectoriesByTypeAndStudyId(String trajectoryType, Integer studyId);

    TrajectoryEntity linkTrajectoryToStudy(Integer trajectoryId, Integer studyId, TrajectoryType type) throws IOException;

    void unlinkTrajectoryFromStudy(Integer trajectoryId, Integer studyId);

    void unlinkBatchTrajectoriesFromStudy(Integer studyId, List<Integer> trajectoryIds);

    void unlinkAllTrajectoriesFromStudy(Integer studyId);

    List<TrajectoryDataDTO> getTrajectoryDataByTypeAndId(TrajectoryType trajectoryType, Integer trajectoryId);

    Map<String, Integer> countWarningMessage(Integer studyId);

    TrajectoryEntity processThermalSpecificParameterTrajectory(String trajectoryToUse, String horizon, String area, Integer studyId) throws Exception;

    TrajectoryEntity processThermalModulationParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws Exception;

    TrajectoryEntity processThermalEconomicCostTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws Exception;

    TrajectoryEntity processThermalEconomicParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws Exception;

}

