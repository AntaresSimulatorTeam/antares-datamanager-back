package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.Type;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.AreaFileProcessorService;
import com.rte_france.antares.datamanager_back.service.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.TrajectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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


    public TrajectoryEntity processTrajectory(Type trajectoryType, String trajectoryToUse) throws IOException {
        File trajectoryFile = getFile(antaressDataManagerProperties.getTrajectoryFilePath() + trajectoryType.name().toLowerCase(), trajectoryToUse + ".xlsx");
        switch (trajectoryType) {
            case AREA -> {
                return areaFileProcessorService.processAreaFile(trajectoryFile);
            }
            case LINK -> {
                return linkFileProcessorService.processLinkFile(trajectoryFile);
            }
            case LOAD -> log.info("not implemented yet");
            // Handle default case
        }
        return null;
    }

    public List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameStartWithFromDB(Type trajectoryType, String fileNameStartsWith) {
        return trajectoryRepository.findTrajectoriesFileNameByTypeAndFileNameStartsWith(trajectoryType.name(), fileNameStartsWith);
    }

    public List<String> findTrajectoriesByTypeAndFileNameStartWithFromFS(Type trajectoryType) {
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
