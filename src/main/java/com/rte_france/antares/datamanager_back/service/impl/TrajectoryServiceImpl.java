package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.AreaFileProcessorService;
import com.rte_france.antares.datamanager_back.service.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.ThermalFileProcessorService;
import com.rte_france.antares.datamanager_back.service.TrajectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


@Slf4j
@Service
@RequiredArgsConstructor
public class TrajectoryServiceImpl implements TrajectoryService {

    private final AreaFileProcessorService areaFileProcessorService;

    private final LinkFileProcessorService linkFileProcessorService;

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    private final TrajectoryRepository trajectoryRepository;

    private final ThermalFileProcessorService thermalFileProcessorService;


    public TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse, String horizon) throws IOException {
        //build the file path
        String filePath = antaressDataManagerProperties.getTrajectoryFilePath() + getDirectoryByTrajectoryType(trajectoryType, null) + File.separator;
        //download the file
        File trajectoryFile = new File(filePath + trajectoryToUse + ".xlsx");
        switch (trajectoryType) {
            case AREA -> {
                return areaFileProcessorService.processAreaFile(trajectoryFile, horizon);
            }
            case LINK -> {
                return linkFileProcessorService.processLinkFile(trajectoryFile, horizon);
            }
            case THERMAL_CAPACITY -> {
                return thermalFileProcessorService.processThermalFile(trajectoryFile, horizon, thermalFileProcessorService::buildThermalClusterCapacityValuesList, trajectoryType);
            }
            case THERMAL_PARAMETER -> {
                return thermalFileProcessorService.processThermalFile(trajectoryFile, horizon, thermalFileProcessorService::buildThermalParameters, trajectoryType);
            }
            case THERMAL_COST -> {
                return thermalFileProcessorService.processThermalFile(trajectoryFile, horizon, thermalFileProcessorService::buildThermalCosts, trajectoryType);
            }
            // Handle default case
            default -> throw new IllegalArgumentException("The provided trajectory type is not supported.");

        }
    }

    public List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameStartWithFromDB(TrajectoryType trajectoryType, String horizon, String fileNameStartsWith) {
        return trajectoryRepository.findTrajectoriesFileNameByTypeAAndHorizonAndFileNameStartsWith(trajectoryType.name(), horizon, fileNameStartsWith);
    }

    public List<FsTrajectoryDTO> findTrajectoriesByTypeAndFileNameStartWithFromFS(TrajectoryType trajectoryType) {
        File directory = new File(antaressDataManagerProperties.getTrajectoryFilePath() + File.separator + trajectoryType.name().toLowerCase());
        if (directory.exists() && directory.isDirectory()) {
            if (directory.listFiles() != null && Objects.requireNonNull(directory.listFiles()).length > 0) {
                return Arrays.stream(Objects.requireNonNull(directory.listFiles()))
                        .filter(File::isFile)
                        .map(file -> FsTrajectoryDTO.builder()
                                .fileName(file.getName())
                                .lastModifiedDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault())).type(trajectoryType.name())
                                .build())
                        .toList();
            }
        } else {
            throw new IllegalArgumentException("The provided path is not a directory or does not exist.");
        }
        return Collections.emptyList();
    }

    @Override
    public List<TrajectoryDTO> findTrajectoriesByTypeAndIds(String trajectoryType, List<Integer> trajectoryIds) {
        return trajectoryRepository.findByTypeAndIdIn(trajectoryType, trajectoryIds).stream()
                .map(TrajectoryMapper::toTrajectoryDTO)
                .toList();
    }

    private String getDirectoryByTrajectoryType(TrajectoryType trajectoryType, String thermalCapacityArea) {
        return switch (trajectoryType) {
            case AREA -> antaressDataManagerProperties.getAreaDirectory();
            case LINK -> antaressDataManagerProperties.getLinkDirectory();
            case THERMAL_COST -> antaressDataManagerProperties.getThermalCostDirectory();
            case THERMAL_CAPACITY ->
                    antaressDataManagerProperties.getThermalCapacityDirectory() + File.separator + thermalCapacityArea;
            case THERMAL_PARAMETER -> antaressDataManagerProperties.getThermalParameterDirectory();
            case LOAD, MISC ->
                    throw new IllegalArgumentException("No directory defined for TrajectoryType: " + trajectoryType);
            default -> throw new IllegalArgumentException("Invalid TrajectoryType: " + trajectoryType);
        };
    }

}
