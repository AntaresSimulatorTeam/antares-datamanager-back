package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.rte_france.antares.datamanager_back.util.Utils.getFile;


@Slf4j
@Service
@RequiredArgsConstructor
public class TrajectoryServiceImpl implements TrajectoryService {

    private final AreaFileProcessorService areaFileProcessorService;

    private final LinkFileProcessorService linkFileProcessorService;

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    private final TrajectoryRepository trajectoryRepository;

    private final ThermalFileProcessorService thermalFileProcessorService;

    public TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse) throws IOException {
        File trajectoryFile = getFile(antaressDataManagerProperties.getTrajectoryFilePath() + trajectoryType.name().toLowerCase(), trajectoryToUse + ".xlsx");
        switch (trajectoryType) {
            case AREA -> {
                return areaFileProcessorService.processAreaFile(trajectoryFile);
            }
            case LINK -> {
                return linkFileProcessorService.processLinkFile(trajectoryFile);
            }
            case THERMAL_CAPACITY -> {
                return thermalFileProcessorService.processThermalCapacityFile(trajectoryFile);
            }
            case THERMAL_PARAMETER -> {
                return thermalFileProcessorService.processThermalParameterFile(trajectoryFile);
            }
            case THERMAL_COST -> {
                return thermalFileProcessorService.processThermalCostFile(trajectoryFile);
            }
            // Handle default case
        }
        return null;
    }

    public List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameStartWithFromDB(TrajectoryType trajectoryType, String fileNameStartsWith) {
        return trajectoryRepository.findTrajectoriesFileNameByTypeAndFileNameStartsWith(trajectoryType.name(), fileNameStartsWith);
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
