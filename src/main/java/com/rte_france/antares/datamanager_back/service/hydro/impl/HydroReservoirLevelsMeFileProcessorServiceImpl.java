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
import com.rte_france.antares.datamanager_back.util.HydroWaterValuesFileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

        validateHydroReservoirLevelsMeDirectory(trajectoryPath, trajectoryToUse, horizon, studyId);

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

    private void validateHydroReservoirLevelsMeDirectory(Path trajectoryPath, String trajectoryName, String horizon, Integer studyId) throws IOException {
        if (!Files.isDirectory(trajectoryPath)) {
            throw BusinessException.builder()
                    .message("Trajectory must be a directory: {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Get all Excel files (*.xlsx) in the directory
        List<Path> excelFilePaths;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(trajectoryPath, "*_reservoir_levels.xlsx")) {
            excelFilePaths = new ArrayList<>();
            stream.forEach(excelFilePaths::add);
        }
        if(excelFilePaths.isEmpty()) {
            throw BusinessException.builder()
                    .message("No Excel files found in the directory: {0}")
                    .errorMessageArguments(List.of(trajectoryName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        List<String> excelFiles = new ArrayList<>();
        String secondPartOfHorizon = horizon.split("-")[1];

        // Validate that each Excel file contains a sheet named with secondPartOfHorizon
        for (Path excelFilePath : excelFilePaths) {
            try (InputStream inputStream = Files.newInputStream(excelFilePath);
                 Workbook workbook = WorkbookFactory.create(inputStream)) {
                Sheet requiredSheet = workbook.getSheet(secondPartOfHorizon);
                if (requiredSheet == null) {
                    throw BusinessException.builder()
                            .message(String.format("Excel file %s does not contain required sheet: %s",
                                    excelFilePath.getFileName().toString(), secondPartOfHorizon))
                            .message("Missing horizon {0} in Reservoir Levels file for {1} in HYDRO_ME Reservoir Levels trajectory {2}")
                            .errorMessageArguments(List.of(secondPartOfHorizon, excelFilePath.getFileName().toString(), trajectoryName))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                excelFiles.add(excelFilePath.getFileName().toString());
            }
        }

        List<String> validNodes = hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId);

        if (!validNodes.isEmpty()) {
            // Match the node portion, not the fixed Reservoir Levels filename text.
            boolean hasAtLeastOneValidFile = excelFiles.stream()
                    .anyMatch(fileName -> validNodes.stream()
                            .anyMatch(nodeName -> matchesNode(fileName, nodeName)));

            if (!hasAtLeastOneValidFile) {
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

    public static boolean matchesNode(String fileName, String node) {
        String normalizedFile = fileName.toLowerCase(Locale.ROOT);
        String normalizedNode = node.trim().toLowerCase(Locale.ROOT);
        return normalizedFile.equals("reservoir_levels_" + normalizedNode + ".xlsx")
                || normalizedFile.equals(normalizedNode + "_reservoir_levels.xlsx");
    }
}
