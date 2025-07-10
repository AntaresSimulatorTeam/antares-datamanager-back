package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.LoadFileProcessorService;
import com.rte_france.antares.datamanager_back.service.WarningService;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoadFileProcessorServiceImpl implements LoadFileProcessorService {

    private final NasFileService nasFileService;
    private final TimeSeriesReader reader;
    private final TimeSeriesWriter writer;
    private final TrajectoryRepository trajectoryRepository;
    private final AreaRepository areaRepository;
    private final WarningService warningService;
    private final LoadRepository loadRepository;

    /**
     * Saves a time series matrix read from the given path to NAS with a unique filename.
     *
     * @param inputPath Path to input .txt file
     * @return Saved filename
     * @throws IOException on read/write failure
     */
    public String saveMatrixToNas(Path inputPath) throws IOException {
        var matrix = reader.readFromTxt(inputPath);
        var outputFileName = generateUniqueFileName(inputPath);
        saveMatrix(outputFileName, matrix);
        setFilePermissions(inputPath);
        return outputFileName;
    }

    private String generateUniqueFileName(Path inputPath) {
        return inputPath.getFileName() + "." + UUID.randomUUID() + "." + writer.getDefaultFileExtension();
    }

    private void saveMatrix(String fileName, TimeSeriesMatrix matrix) throws IOException {
        byte[] data = writer.writeToByteArray(matrix);
        nasFileService.saveFile(fileName, data);
    }

    private void setFilePermissions(Path path) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }

    @Override
    public Set<WarningMessageEntity> checkForMissingLoadFiles(Path trajectoryPath, String horizon, Integer studyId,
                                                              String userNni, TrajectoryEntity trajectory) {
        return checkMissingLoad(
                studyId,
                userNni,
                trajectory,
                getFileCheckerByPath(trajectoryPath, horizon)
        );
    }

    @Override
    public Set<WarningMessageEntity> checkForMissingLoadByAreaFromDb(String horizon, Integer studyId,
                                                                     String userNni, TrajectoryEntity trajectory) {
        return checkMissingLoad(
                studyId,
                userNni,
                trajectory,
                getFileCheckerByDatabase(horizon, trajectory, studyId)
        );
    }

    /**
     * Main logic for both checkForMissingLoadFiles and checkForMissingLoadByAreaFromDb
     */
    private Set<WarningMessageEntity> checkMissingLoad(Integer studyId, String userNni,
                                                       TrajectoryEntity trajectory,
                                                       LoadChecker loadChecker) {
        List<String> areasWithoutTrajectory = getAreasLoadWithoutTrajectorySelected(studyId);

        List<String> missingLoadFiles = areasWithoutTrajectory.stream()
                .filter(area -> !loadChecker.exists(area.toLowerCase()))
                .toList();

        if (!areasWithoutTrajectory.isEmpty() && missingLoadFiles.size() == areasWithoutTrajectory.size()) {
            throw BusinessException.builder()
                    .message("Missing file(s) for area(s) {0} in LOAD Other areas trajectory {1}")
                    .errorMessageArguments(Arrays.asList(
                            String.join(", ", areasWithoutTrajectory),
                            trajectory.getFileName()
                    ))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        Set<WarningMessageEntity> warningMessages = new HashSet<>();
        if (!missingLoadFiles.isEmpty()) {
            warningService.addWarning(warningMessages,
                    Arrays.asList(String.join(", ", missingLoadFiles), trajectory.getFileName()),
                    WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS,
                    studyId,
                    userNni,
                    trajectory
            );
        }

        return warningMessages;
    }

    public List<String> getAreasLoadWithoutTrajectorySelected(Integer studyId) {
        List<String> studyAreas = areaRepository.findAllByStudyId(studyId).stream()
                .map(AreaEntity::getName)
                .toList();

        List<String> areasWithTrajectory = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LOAD.name(), studyId)
                .stream()
                .map(TrajectoryEntity::getLoadArea)
                .toList();

        return studyAreas.stream()
                .filter(area -> !areasWithTrajectory.contains(area))
                .toList();
    }

    /**
     * Returns a LoadChecker that checks for file existence on disk.
     */
    private LoadChecker getFileCheckerByPath(Path trajectoryPath, String horizon) {
        return area -> Files.exists(trajectoryPath.resolve("load_" + area.toLowerCase() + "_" + horizon + ".txt"));
    }

    /**
     * Returns a LoadChecker that checks for file existence in the database.
     * If the file does not exist, it creates a new LoadEntity and saves it.
     */
    /**
     * Returns a LoadChecker that checks for file existence in the DB.
     */
    private LoadChecker getFileCheckerByDatabase(String horizon, TrajectoryEntity trajectory, Integer studyId) {
        return area -> loadRepository.existsByFileName(
                "load_" + area.toLowerCase() + "_" + horizon + ".txt"
        );
    }

    @FunctionalInterface
    private interface LoadChecker {
        boolean exists(String area);
    }
}
