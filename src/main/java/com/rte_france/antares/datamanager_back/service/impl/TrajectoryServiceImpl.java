package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.configuration.SftpDownloadService;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.AreaFileProcessorService;
import com.rte_france.antares.datamanager_back.service.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.ThermalFileProcessorService;
import com.rte_france.antares.datamanager_back.service.TrajectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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

    private final SftpDownloadService sftpDownloadService;

    public TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse, String horizon) throws IOException {
        //build the file path
        String filePath = antaressDataManagerProperties.getDataRemoteDirectory() + sftpDownloadService.getDirectoryByTrajectoryType(trajectoryType, null)+ File.separator;
        //download the file
        File trajectoryFile = sftpDownloadService.downloadFile(filePath + trajectoryToUse + ".xlsx");
        switch (trajectoryType) {
            case AREA -> {
                return areaFileProcessorService.processAreaFile(trajectoryFile,horizon);
            }
            case LINK -> {
                return linkFileProcessorService.processLinkFile(trajectoryFile, horizon);
            }
            case THERMAL_CAPACITY -> {
                return thermalFileProcessorService.processThermalCapacityFile(trajectoryFile, horizon);
            }
            case THERMAL_PARAMETER -> {
                return thermalFileProcessorService.processThermalParameterFile(trajectoryFile,horizon);
            }
            case THERMAL_COST -> {
                return thermalFileProcessorService.processThermalCostFile(trajectoryFile,horizon);
            }
            // Handle default case
        }
        return null;
    }

    public List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameStartWithFromDB(TrajectoryType trajectoryType, String horizon, String fileNameStartsWith) {
        return trajectoryRepository.findTrajectoriesFileNameByTypeAAndHorizonAndFileNameStartsWith(trajectoryType.name(), horizon, fileNameStartsWith);
    }

    public List<String> findTrajectoriesByTypeAndFileNameStartWithFromFS(TrajectoryType trajectoryType) {
        File directory = new File(antaressDataManagerProperties.getTrajectoryFilePath() + trajectoryType.name().toLowerCase());
        if (directory.exists() && directory.isDirectory()) {
            if (directory.listFiles() != null && Objects.requireNonNull(directory.listFiles()).length > 0) {
                return Arrays.stream(Objects.requireNonNull(directory.listFiles()))
                        .filter(File::isFile)
                        .map(File::getName)
                        .toList();
            }
        } else {
            throw new IllegalArgumentException("The provided path is not a directory or does not exist.");
        }
        return Collections.emptyList();
    }


}
