package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.HydroCapacityMeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.hydro.HydroReservoirLevelsMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.util.Utils.computeChecksumByType;
import static com.rte_france.antares.datamanager_back.util.Utils.isSameFileWithSameContent;
import static com.rte_france.antares.datamanager_back.util.Utils.calculateDirectorySize;

@Slf4j
@Service
@RequiredArgsConstructor
public class HydroReservoirLevelsMeFileProcessorServiceImpl implements HydroReservoirLevelsMeFileProcessorService {

    private final TrajectoryRepository trajectoryRepository;
    private final HydroCapacityMeRepository hydroCapacityMeRepository;
    private final UserService userService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;

    private static final String RESERVOIR_LEVELS_FILE = "reservoir_levels.xlsx";


    @Transactional(rollbackFor = {IOException.class})
    @Override
    public TrajectoryEntity processHydroReservoirLevelsMeFile(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveHydroReservoirLevelsMeTrajectoryInDb(trajectoryToUse, horizon, studyId);
    }

    public TrajectoryEntity saveHydroReservoirLevelsMeTrajectoryInDb(String trajectoryToUse, String horizon, Integer studyId) throws IOException {

        String userNni = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElseThrow(() ->
                        BusinessException.builder()
                                .message("User NNI could not be determined")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build());

        Path trajectoryPath = buildTrajectoryPath(trajectoryToUse);

        validateHydroReservoirLevelsMeDirectory(trajectoryPath, trajectoryToUse, studyId);

        Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                .findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(trajectoryToUse, horizon, TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name());

        if (existingTrajectoryOpt.isPresent()) {
            TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
            if (isSameFileWithSameContent(trajectoryPath, existingTrajectory)) {
                throw BusinessException.builder()
                        .message("File already processed with same content: {0}")
                        .errorMessageArguments(List.of(trajectoryToUse))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            TrajectoryEntity newTrajectory = buildNewHydroReservoirLevelsMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
            newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
            return trajectoryRepository.save(newTrajectory);
        }

        TrajectoryEntity newTrajectory = buildNewHydroReservoirLevelsMeTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
        return trajectoryRepository.save(newTrajectory);
    }

    private void validateHydroReservoirLevelsMeDirectory(Path trajectoryPath, String trajectoryName, Integer studyId) throws IOException {
        if (!Files.isDirectory(trajectoryPath)) {
            throw BusinessException.builder()
                    .message("Trajectory must be a directory: {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Get all files (representing nodes)
        List<Path> nodeFiles;
        try (Stream<Path> files = Files.list(trajectoryPath)) {
            nodeFiles = files
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        }

        if (nodeFiles.isEmpty()) {
            throw BusinessException.builder()
                    .message("No node files found in HYDRO_RESERVOIR_LEVELS_ME trajectory: {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Get valid nodes from HYDRO_CAPACITY_ME associated to the study
        List<String> validNodes = hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId)
                .stream().map(node -> node+"_"+RESERVOIR_LEVELS_FILE)
                .collect(Collectors.toList());
        if (!validNodes.isEmpty()) {
            // Check if at least one node directory exists in validNodes
            boolean hasAtLeastOneValidNode = nodeFiles.stream()
                    .anyMatch(nodeFile -> validNodes.contains(nodeFile.getFileName().toString()));

            if (!hasAtLeastOneValidNode) {
                throw BusinessException.builder()
                        .message("No file related to the nodes of the HYDRO_ME_CAPACITY trajectory in HYDRO_ME Reservoir Levels trajectory {0}")
                        .errorMessageArguments(List.of(trajectoryName))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

        }
    }

    private Path buildTrajectoryPath(String trajectoryToUse) throws IOException {
        String nasDir = antaresDataManagerProperties.getNasDirectory();
        String trajFilePath = antaresDataManagerProperties.getTrajectoryFilePath();
        String directoryByType = antaresDataManagerProperties.getHydroReservoirLevelsMeDirectory();

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

    private TrajectoryEntity buildNewHydroReservoirLevelsMeTrajectory(String trajectoryToUse, String horizon, Path trajectoryPath, String userNni) throws IOException {
        long totalSize = calculateDirectorySize(trajectoryPath);

        return TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(totalSize)
                .createdBy(userNni)
                .version(1)
                .lastModificationContentDate(Files.getLastModifiedTime(trajectoryPath).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .horizon(horizon)
                .checksum(computeChecksumByType(trajectoryPath, TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME, horizon, null))
                .type(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name())
                .creationDate(LocalDateTime.now())
                .build();
    }
}
