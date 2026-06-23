package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.service.hydro.HydroMessageHelper;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroSeriesEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.hydro.HydroCoherenceCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HydroCoherenceCheckServiceImpl implements HydroCoherenceCheckService {
    private final TrajectoryRepository trajectoryRepository;
    private final HydroAllocationRepository hydroAllocationRepository;
    private final HydroParametersRepository hydroParametersRepository;
    private final HydroSeriesRepository hydroSeriesRepository;

    protected static final String HYDRO_SERIES_INFLOWS_MOD = "mod";
    private static final String TRAJECTORY_LABEL = " trajectory ";
    
    @Override
    public void checkHydroSeriesTrajectoriesConsistency(Integer studyId, List<String> areasInHydroSeriesFiles, String areaParam, String trajectoryToUse, String seriesTrajectoryType) {
        String targetTpType = getAssociatedTechnicalType(seriesTrajectoryType);
        boolean isPsp = seriesTrajectoryType.contains("PSP");
        TrajectoryEntity tpTrajectory = trajectoryRepository.findLatestByStudyIdAndAreaAndType(studyId, areaParam, targetTpType);
            if (tpTrajectory != null) {
                List<String> areasInHydroAllocationEntities = getAreasInHydroAllocationAreas(tpTrajectory.getId());
                boolean isHydroAllocationTrajectoryHasAreas = areasInHydroAllocationEntities.containsAll(areasInHydroSeriesFiles);
                if (isHydroAllocationTrajectoryHasAreas) {
                    List<String> areasInHydroParametersEntities = getAreasInHydroParametersAreas(tpTrajectory.getId());
                    boolean isHydroParametersTrajectoryHasAreas = areasInHydroParametersEntities.containsAll(areasInHydroSeriesFiles);
                    if (!isHydroParametersTrajectoryHasAreas) {
                        String label = HydroMessageHelper.getFileLabel("hydroParameters", isPsp);
                        throw BusinessException.builder()
                                .message("Missing areas " + label + " in " + HydroMessageHelper.getTechnicalParametersLabel(isPsp) + TRAJECTORY_LABEL + trajectoryToUse)
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build();
                    }
                } else {
                    String label = HydroMessageHelper.getFileLabel("hydroAllocation", isPsp);
                    throw BusinessException.builder()
                            .message("Missing areas " + label + " in " + HydroMessageHelper.getTechnicalParametersLabel(isPsp) + TRAJECTORY_LABEL + trajectoryToUse)
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            }
    }

    @Override
    public void checkHydroTPTrajectoriesConsistency(Integer studyId, List<String> areasTPFiles, String areaParam, String trajectoryToUse, String childTrajectoryType, String parentTrajectoryType) {
        String targetSeriesType = getAssociatedSeriesType(parentTrajectoryType);
        boolean isPsp = parentTrajectoryType.contains("PSP");
        TrajectoryEntity seriesTrajectory = trajectoryRepository.findLatestByStudyIdAndAreaAndType(studyId, areaParam, targetSeriesType);
        if (seriesTrajectory != null) {
            List<String> areasModInHydroSeriesTrajectory = getAreasInHydroSeriesModFiles(seriesTrajectory.getId());
            boolean isHydroSeriesTrajectoryHasTPAreas = areasTPFiles.containsAll(areasModInHydroSeriesTrajectory);

            if (!isHydroSeriesTrajectoryHasTPAreas) {
                String childLabel = Objects.equals(childTrajectoryType, TrajectoryType.HYDRO_ALLOCATION.name()) ? "hydroAllocation" : "hydroParameters";
                String label = HydroMessageHelper.getFileLabel(childLabel, isPsp);
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(label))
                        .message("Missing areas {0} in " + HydroMessageHelper.getTechnicalParametersLabel(isPsp) + TRAJECTORY_LABEL + trajectoryToUse)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }
    
    public List<String> getAreasInHydroSeriesModFiles(Integer trajectoryId) {
        List<HydroSeriesEntity> hydroSeriesEntities = hydroSeriesRepository.findHydroSeriesEntitiesByTrajectoryId(trajectoryId);
        return hydroSeriesEntities.stream()
                .map(HydroSeriesEntity::getTsName)
                .filter(file -> file.startsWith(HYDRO_SERIES_INFLOWS_MOD))
                .map(s -> s.split("_")[1])
                .toList();
    }

    public List<String> getAreasInHydroAllocationAreas(Integer trajectoryId) {
        List<HydroAllocationEntity> hydroAllocationEntities = hydroAllocationRepository.findHydroAllocationEntitiesByTrajectoryId(trajectoryId);
        return hydroAllocationEntities.stream()
                .map(HydroAllocationEntity::getHydro)
                .toList();
    }

    public List<String> getAreasInHydroParametersAreas(Integer trajectoryId) {
        List<HydroParametersEntity> hydroParametersEntities = hydroParametersRepository.findHydroParametersEntitiesByTrajectoryId(trajectoryId);
        return hydroParametersEntities.stream()
                .map(HydroParametersEntity::getNode)
                .toList();
    }

    public void validateHydroSeriesCoherence(Integer studyId, TrajectoryEntity trajectory) {
        List<String> areasInSeriesMod = getAreasInHydroSeriesModFiles(trajectory.getId());
        checkHydroSeriesTrajectoriesConsistency(studyId, areasInSeriesMod, trajectory.getArea(), trajectory.getFileName(), trajectory.getType());
    }

    public void validateHydroTechnicalParametersCoherence(Integer studyId, TrajectoryEntity trajectory, TrajectoryType hydroType) {
        List<String> areasTPFiles = getHydroTechnicalParameterAreas(trajectory.getId(), hydroType);
        checkHydroTPTrajectoriesConsistency(studyId, areasTPFiles, trajectory.getArea(), trajectory.getFileName(), hydroType.name(), trajectory.getType());
    }

    public List<String> getHydroTechnicalParameterAreas(Integer trajectoryId, TrajectoryType hydroType) {
        if (TrajectoryType.HYDRO_ALLOCATION.equals(hydroType)) {
            return getAreasInHydroAllocationAreas(trajectoryId);
        }

        return getAreasInHydroParametersAreas(trajectoryId);
    }
    private String getAssociatedTechnicalType(String seriesType) {
        return TrajectoryType.HYDRO_PSP_SERIES.name().equals(seriesType)
                ? TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name()
                : TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name();
    }

    private String getAssociatedSeriesType(String techType) {
        return TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name().equals(techType)
                ? TrajectoryType.HYDRO_PSP_SERIES.name()
                : TrajectoryType.HYDRO_SERIES.name();
    }
}