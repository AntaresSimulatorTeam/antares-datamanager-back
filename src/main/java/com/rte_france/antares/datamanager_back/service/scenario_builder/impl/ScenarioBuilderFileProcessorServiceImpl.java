package com.rte_france.antares.datamanager_back.service.scenario_builder.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ScenarioBuilderRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.ScenarioBuilderEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.scenario_builder.ScenarioBuilderFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.SCENARIO_BUILDER;
import static com.rte_france.antares.datamanager_back.util.Utils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioBuilderFileProcessorServiceImpl implements ScenarioBuilderFileProcessorService {

    private static final String UNKNOWN_USER = "UNKNOWN";
    private static final String SCENARIO_BUILDER_FILE_SUFFIX = ".xlsx";

    private final TrajectoryRepository trajectoryRepository;
    private final ScenarioBuilderRepository scenarioBuilderRepository;
    private final TrajectoryServiceImpl trajectoryService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final UserService userService;
    private final PathSecurityUtil pathSecurityUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrajectoryEntity processScenarioBuilderFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        String scenarioBuilderDirectory = antaresDataManagerProperties.getScenarioBuilderDirectory();


        Path basePath = Path.of(antaresDataManagerProperties.getNasDirectory()).resolve(antaresDataManagerProperties.getTrajectoryFilePath());
        Path trajectoryFolder = basePath.resolve(scenarioBuilderDirectory).normalize();

        log.info("Loading scenario builder from: {}", trajectoryFolder);

        if (!Files.isDirectory(trajectoryFolder)) {
            throw BusinessException.builder()
                    .message("Scenario builder folder not found: {0}")
                    .errorMessageArguments(List.of(trajectoryFolder.toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        String fileNameWithExt = trajectoryToUse.endsWith(SCENARIO_BUILDER_FILE_SUFFIX)
                ? trajectoryToUse
                : trajectoryToUse + SCENARIO_BUILDER_FILE_SUFFIX;
        Path filePath = trajectoryFolder.resolve(fileNameWithExt).normalize();

        if (!Files.isRegularFile(filePath)) {
            throw BusinessException.builder()
                    .message("Scenario builder file not found: {0}")
                    .errorMessageArguments(List.of(filePath.toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        String checksum = getFileChecksum(filePath.toString());
        String trajectoryFileName = getFileNameWithoutExtensionAndWithoutPrefix(filePath.getFileName().toString(), SCENARIO_BUILDER.name());

        Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryFileName,
                civilToChevalHorizon(horizon),
                SCENARIO_BUILDER.name()
        );

        if (existingTrajectoryOpt.isEmpty()) {
            existingTrajectoryOpt = trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                    trajectoryFileName,
                    horizon,
                    SCENARIO_BUILDER.name()
            );
        }

        int version = 1;
        if (existingTrajectoryOpt.isPresent()) {
            TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
            if (checksum.equals(existingTrajectory.getChecksum())) {
                throw BusinessException.builder()
                        .message("File already processed with same content {0}")
                        .errorMessageArguments(List.of(filePath.getFileName().toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            } else {
                version = existingTrajectory.getVersion() + 1;
            }
        }


        UserInfoDto currentUserDetails =
                userService != null ? userService.getCurrentUserDetails() : null;

        String createdBy = currentUserDetails != null
                ? currentUserDetails.getNni()
                : UNKNOWN_USER;

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .fileName(trajectoryFileName)
                .fileSize(Files.size(filePath))
                .creationDate(LocalDateTime.now())
                .createdBy(createdBy)
                .version(version)
                .checksum(checksum)
                .lastModificationContentDate(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(filePath).toMillis()),
                        ZoneId.systemDefault()
                ))
                .horizon(civilToChevalHorizon(horizon))
                .type(SCENARIO_BUILDER.name())
                .build();

        TrajectoryEntity savedTrajectory = trajectoryRepository.save(trajectory);

        List<ScenarioBuilderEntity> entities = readScenarioBuilderFile(filePath, savedTrajectory);
        scenarioBuilderRepository.saveAll(entities);

        if (studyId != null) {
            trajectoryService.linkTrajectoryToStudy(savedTrajectory.getId(), studyId, SCENARIO_BUILDER);
        }

        log.info("Scenario builder trajectory {} imported successfully (id: {}, version: {}, rows: {})",
                trajectoryFileName, savedTrajectory.getId(), savedTrajectory.getVersion(), entities.size());

        return savedTrajectory;
    }

    private List<ScenarioBuilderEntity> readScenarioBuilderFile(Path filePath, TrajectoryEntity trajectory) throws IOException {
        List<ScenarioBuilderEntity> entities = new ArrayList<>();
        DataFormatter dataFormatter = new DataFormatter();

        try (InputStream inputStream = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            String currentCategory = "";
            for (Row row : sheet) {
                if (row == null) continue;
                Cell cell = row.getCell(0);
                if (cell == null) continue;

                String cellValue = dataFormatter.formatCellValue(cell);
                if (cellValue == null || cellValue.isBlank()) continue;

                String trimmed = cellValue.trim();
                if (trimmed.contains("[") && trimmed.contains("]")) {
                    currentCategory = trimmed.substring(trimmed.indexOf('[') + 1, trimmed.lastIndexOf(']')).trim();
                    continue;
                }

                String cleaned = trimmed.replace("@", "").replace("*", "").trim();

                ScenarioBuilderEntity entity = ScenarioBuilderEntity.builder()
                        .category(currentCategory)
                        .modulo(cleaned)
                        .trajectory(trajectory)
                        .build();
                entities.add(entity);
            }
        }

        return entities;
    }
}
