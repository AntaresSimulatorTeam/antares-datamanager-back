package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.LoadFileProcessorService;
import com.rte_france.antares.datamanager_back.service.WarningService;
import com.rte_france.antares.datamanager_back.util.Utils;
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
import java.util.stream.Collectors;

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

    private static final String OTHER_AREA = "OTHERS";

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
    public List<String> getAreasLoadWithoutTrajectorySelected(Integer studyId) {
        List<String> studyAreas = areaRepository.findAllByStudyId(studyId).stream()
                .map(AreaEntity::getName)
                .map(Utils::normalize)
                .distinct()
                .toList();

        Set<String> customAreas = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LOAD.name(), studyId).stream()
                .map(TrajectoryEntity::getLoadArea)
                .filter(Objects::nonNull)
                .map(Utils::normalize)
                .filter(a -> !OTHER_AREA.equals(a))
                .collect(Collectors.toSet());

        return studyAreas.stream()
                .filter(a -> !customAreas.contains(a))
                .toList();
    }


    private Set<WarningMessageEntity> checkMissingLoad(Integer studyId,
                                                       String userNni,
                                                       TrajectoryEntity trajectory,
                                                       LoadChecker loadChecker) {
        List<String> impactedAreas = getAreasLoadWithoutTrajectorySelected(studyId).stream()
                .map(Utils::normalize)
                .toList();

        LoadChecker normalizedChecker = area -> loadChecker.exists(Utils.normalize(area));

        List<String> missingLoadFiles = impactedAreas.stream()
                .filter(area -> !normalizedChecker.exists(area))
                .toList();

        if (!impactedAreas.isEmpty() && missingLoadFiles.size() == impactedAreas.size()) {
            throw BusinessException.builder()
                    .message("Missing file(s) for area(s) {0} in LOAD Other areas trajectory {1}\n" +
                            "Please re import trajectory {1} to complete area(s)")
                    .errorMessageArguments(Arrays.asList(
                            String.join(", ", impactedAreas),
                            trajectory.getFileName()
                    ))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        Set<WarningMessageEntity> warningMessages = new HashSet<>();
        if (!missingLoadFiles.isEmpty()) {
            warningService.addWarning(
                    warningMessages,
                    Arrays.asList(String.join(", ", missingLoadFiles), trajectory.getFileName()),
                    WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS,
                    studyId,
                    userNni,
                    trajectory
            );
        }

        return warningMessages;
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
    private LoadChecker getFileCheckerByDatabase(String horizon, TrajectoryEntity trajectory, Integer studyId) {
        if (trajectory.getLoadEntities() != null && !trajectory.getLoadEntities().isEmpty()) {
            Set<String> areasInThisOthers = trajectory.getLoadEntities().stream()
                    .map(LoadEntity::getArea)
                    .filter(Objects::nonNull)
                    .map(Utils::normalize)
                    .collect(Collectors.toSet());

            return area -> areasInThisOthers.contains(Utils.normalize(area));
        }

        String trajFileName = trajectory.getFileName();
        return area -> {
            String fn = "load_" + Utils.normalize(area) + "_" + horizon + ".txt";
            return loadRepository.findByFileNameAndTrajectoryFileName(fn, trajFileName).isPresent();
        };
    }


    @FunctionalInterface
    private interface LoadChecker {
        boolean exists(String area);
    }
}
