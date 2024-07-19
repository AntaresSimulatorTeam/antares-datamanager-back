package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.Type;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.rte_france.antares.datamanager_back.util.Utils.getFile;


@Slf4j
@Service
@Getter
@Configuration
@AllArgsConstructor
public class TrajectoryProcessorService {

    private final AreaFileProcessorService areaFileProcessorService;

    private final LinkFileProcessorService linkFileProcessorService;

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    private final TrajectoryRepository trajectoryRepository;


    public void processTrajectory(Type trajectoryType, String trajectoryToUse) throws IOException {
        File trajectoryFile = getFile(antaressDataManagerProperties.getTrajectoryFilePath() + trajectoryType.name().toLowerCase(),  trajectoryToUse+ ".xlsx");
        switch (trajectoryType) {
            case AREA -> areaFileProcessorService.processAreaFile(trajectoryFile);
            case LINK -> linkFileProcessorService.processLinkFile(trajectoryFile);
            case LOAD -> log.info("not implemented yet");
            default -> {
            }
            // Handle default case
        }
    }

    public List<String> findTrajectoriesByType(Type trajectoryType) {
        return trajectoryRepository.findTrajectoryEntitiesByType(trajectoryType.toString()).stream()
                .map(TrajectoryEntity::getFileName)
                .toList();
    }
}
