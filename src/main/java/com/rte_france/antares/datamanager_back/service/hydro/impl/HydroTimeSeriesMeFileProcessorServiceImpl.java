package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.HydroCapacityMeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.hydro.HydroTimeSeriesMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static com.rte_france.antares.datamanager_back.util.Utils.computeChecksumByType;
import static com.rte_france.antares.datamanager_back.util.Utils.isSameFileWithSameContent;

@Slf4j
@Service
@RequiredArgsConstructor
public class HydroTimeSeriesMeFileProcessorServiceImpl implements HydroTimeSeriesMeFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final HydroCapacityMeRepository hydroCapacityMeRepository;
    private final UserService userService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;

    private static final String MOD_FILE = "mod.xlsx";
    private static final String ROR_FILE = "ror.xlsx";

    @Transactional
    @Override
    public TrajectoryEntity processHydroTimeSeriesMeDirectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveHydroTimeSeriesMeTrajectoryInDb(trajectoryToUse, horizon, studyId);
    }

    public TrajectoryEntity saveHydroTimeSeriesMeTrajectoryInDb(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        String userNni = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElseThrow(() ->
                        BusinessException.builder()
                                .message("User NNI could not be determined")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build());

        Path trajectoryPath = buildTrajectoryPath(trajectoryToUse);

        validateHydroTimeSeriesMeDirectory(trajectoryPath, trajectoryToUse, studyId);

        Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(trajectoryToUse, horizon, TrajectoryType.HYDRO_TIME_SERIES_ME.name());

        if (existingTrajectoryOpt.isPresent()) {
            TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
            if (isSameFileWithSameContent(trajectoryPath, existingTrajectory)) {
                throw BusinessException.builder()
                        .message("Directory already processed with same content: {0}")
                        .errorMessageArguments(List.of(trajectoryToUse))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            TrajectoryEntity newTrajectory = buildNewHydroTimeSeriesMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
            newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
            return trajectoryRepository.save(newTrajectory);
        }

        TrajectoryEntity newTrajectory = buildNewHydroTimeSeriesMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
        return trajectoryRepository.save(newTrajectory);
    }

    private void validateHydroTimeSeriesMeDirectory(Path trajectoryPath, String trajectoryName, Integer studyId) throws IOException {
        if (!Files.isDirectory(trajectoryPath)) {
            throw BusinessException.builder()
                    .message("Trajectory must be a directory: {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Get all subdirectories (representing nodes)
        List<Path> nodeDirectories;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(trajectoryPath, Files::isDirectory)) {
            nodeDirectories = new ArrayList<>();
            stream.forEach(nodeDirectories::add);
        }

        if (nodeDirectories.isEmpty()) {
            throw BusinessException.builder()
                    .message("No node directories found in HYDRO_TIME_SERIES_ME trajectory: {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Get valid nodes from HYDRO_CAPACITY_ME associated to the study
        List<String> validNodes = hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId);
        if (!validNodes.isEmpty()) {
            // Check if at least one node directory exists in validNodes
            boolean hasAtLeastOneValidNode = nodeDirectories.stream()
                    .anyMatch(nodeDir -> validNodes.contains(nodeDir.getFileName().toString()));

            if (!hasAtLeastOneValidNode) {
                throw BusinessException.builder()
                        .message("No data related to the nodes of the HYDRO_ME_CAPACITY trajectory in HYDRO_ME Reservoir Levels trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Validate each node directory that exists in validNodes
            for (Path nodeDir : nodeDirectories) {
                String nodeName = nodeDir.getFileName().toString();

                // Only validate files if node exists in HYDRO_CAPACITY_ME
                if (validNodes.contains(nodeName)) {
                    validateNodeDirectory(nodeDir, nodeName, trajectoryName);
                }
            }
        }
    }

    private void validateNodeDirectory(Path nodeDir, String nodeName, String trajectoryName) throws IOException {
        // Check if mod.xlsx exists
        Path modFile = nodeDir.resolve(MOD_FILE);
        if (!Files.exists(modFile) || !Files.isRegularFile(modFile)) {
            throw BusinessException.builder()
                    .message("Missing " + MOD_FILE + " in node directory: {0} in trajectory: {1}")
                    .errorMessageArguments(List.of(nodeName, trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Check if ror.xlsx exists
        Path rorFile = nodeDir.resolve(ROR_FILE);
        if (!Files.exists(rorFile) || !Files.isRegularFile(rorFile)) {
            throw BusinessException.builder()
                    .message("Missing " + ROR_FILE + " in node directory: {0} in trajectory: {1}")
                    .errorMessageArguments(List.of(nodeName, trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private Path buildTrajectoryPath(String trajectoryToUse) throws IOException {
        String nasDir = antaresDataManagerProperties.getNasDirectory();
        String trajFilePath = antaresDataManagerProperties.getTrajectoryFilePath();
        String directoryByType = antaresDataManagerProperties.getHydroTimeSeriesMeDirectory();

        if (nasDir == null || trajFilePath == null || directoryByType == null) {
            throw BusinessException.builder()
                    .message("Antares path configuration is incomplete")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        Path baseDirectory = Path.of(nasDir)
                .resolve(trajFilePath)
                .resolve(directoryByType)
                .normalize();

        Path trajectoryDirectoryPath = baseDirectory.resolve(trajectoryToUse).normalize();
        if (!trajectoryDirectoryPath.startsWith(baseDirectory)) {
            throw new IOException("Path is outside of the target directory");
        }

        return trajectoryDirectoryPath;
    }

    private TrajectoryEntity buildNewHydroTimeSeriesMeTrajectory(String trajectoryToUse, String horizon, Path trajectoryPath, String userNni) throws IOException {
        long totalSize = calculateDirectorySize(trajectoryPath);

        return TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(totalSize)
                .createdBy(userNni)
                .version(1)
                .lastModificationContentDate(Files.getLastModifiedTime(trajectoryPath).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .horizon(horizon)
                .checksum(computeChecksumByType(trajectoryPath, TrajectoryType.HYDRO_TIME_SERIES_ME, horizon, null))
                .type(TrajectoryType.HYDRO_TIME_SERIES_ME.name())
                .creationDate(LocalDateTime.now())
                .build();
    }

    private long calculateDirectorySize(Path directory) throws IOException {
        long size = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (Files.isDirectory(file)) {
                    size += calculateDirectorySize(file);
                } else {
                    size += Files.size(file);
                }
            }
        }
        return size;
    }
}


