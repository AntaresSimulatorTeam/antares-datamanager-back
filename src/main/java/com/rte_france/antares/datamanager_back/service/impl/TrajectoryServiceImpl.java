package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.*;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.AreaMapper;
import com.rte_france.antares.datamanager_back.mapper.LinkMapper;
import com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.Utils.getValidLoadFileNamesWithHorizon;
import static com.rte_france.antares.datamanager_back.util.Utils.isSameLoadTrajectory;


@Slf4j
@Service
@RequiredArgsConstructor
public class TrajectoryServiceImpl implements TrajectoryService {

    private final AreaFileProcessorService areaFileProcessorService;

    private final LinkFileProcessorService linkFileProcessorService;

    private final AntaressDataManagerProperties antaressDataManagerProperties;

    private final TrajectoryRepository trajectoryRepository;

    private final ThermalFileProcessorService thermalFileProcessorService;

    private final LoadFileProcessorService loadFileProcessorService;

    private final StudyRepository studyRepository;

    private final StudyTrajectoryRepository studyTrajectoryRepository;

    private final AreaConfigRepository areaConfigRepository;

    private final AreaRepository areaRepository;

    private final LinkRepository linkRepository;

    private final WarningMessageRepository warningMessageRepository;

    private final UserService userService;

    private static final String AREAS_PREFIX = "areas_";
    private static final String LINKS_PREFIX = "links_";

    @Transactional
    @Override
    public TrajectoryEntity processLoadTrajectory(String area, String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        TrajectoryEntity savedTrajectory = saveLoadTrajectoriesInDb(area, trajectoryToUse, horizon, studyId);
        Path trajectoryPath = buildTrajectoryPath(area, trajectoryToUse);
        savedTrajectory.getLoadEntities().forEach(load -> {
            try {
                String outputFileName = loadFileProcessorService.saveMatrixToNas(trajectoryPath.resolve(load.getFileName()));
                load.setOutPutFileName(outputFileName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return savedTrajectory;
    }

    /**
     * Processes a load trajectory file based on the given area, trajectory name, and horizon.
     *
     * @param area            the area of the trajectory
     * @param trajectoryToUse the name of the trajectory file to use
     * @param horizon         the horizon period in the format yyyy-yyyy
     * @return the processed TrajectoryEntity
     * @throws IOException if an I/O error occurs
     */
    public TrajectoryEntity saveLoadTrajectoriesInDb(String area, String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        if (area == null || trajectoryToUse == null || horizon == null) {
            throw
                    BusinessException.builder()
                            .message("Area, trajectory name, and horizon must not be null")
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();

        }
        areaRepository.findAreaByNameAndStudyId(area, studyId).orElseThrow(() ->
                BusinessException.builder()
                        .message("Area not found for studyId: {0} ")
                        .errorMessageArguments(List.of(studyId.toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());

        String userNni = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElseThrow(() ->
                        BusinessException.builder()
                                .message("User NNI could not be determined")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build());

        // Build and normalize the trajectory path
        Path trajectoryPath = buildTrajectoryPath(area, trajectoryToUse);

        // Try to find existing trajectory
        Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                .findFirstByFileNameAndHorizonOrderByVersionDesc(trajectoryToUse, horizon);

        if (existingTrajectoryOpt.isPresent()) {
            TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();

            if (isSameLoadTrajectory(trajectoryPath, existingTrajectory)) {
                throw BusinessException.builder()
                        .message("Trajectory already uploaded")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Update version and save new trajectory
            TrajectoryEntity newTrajectory = buildNewLoadTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
            newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
            return buildAndSaveLoadTrajectory(area, horizon, trajectoryPath, newTrajectory);
        }

        // No existing trajectory: create and save new
        TrajectoryEntity newTrajectory = buildNewLoadTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
        return buildAndSaveLoadTrajectory(area, horizon, trajectoryPath, newTrajectory);
    }

    // Utility method to build trajectory path with checks
    private Path buildTrajectoryPath(String area, String trajectoryToUse) {
        String nasDir = antaressDataManagerProperties.getNasDirectory();
        String trajFilePath = antaressDataManagerProperties.getTrajectoryFilePath();
        String loadDir = antaressDataManagerProperties.getLoadDirectory();

        if (nasDir == null || trajFilePath == null || loadDir == null) {
            throw BusinessException.builder()
                    .message("Antaress path configuration is incomplete")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        return Paths.get(nasDir)
                .resolve(trajFilePath)
                .resolve(loadDir)
                .resolve("load_" + area.toUpperCase())
                .resolve(trajectoryToUse)
                .normalize();
    }


    private TrajectoryEntity buildNewLoadTrajectory(String trajectoryToUse, String horizon, Path trajectoryPath, String userNni) throws IOException {
        //build new trajectory
        return TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(Files.size(trajectoryPath))
                .createdBy(userNni)
                .version(0)
                .lastModificationContentDate(Files.getLastModifiedTime(trajectoryPath).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .horizon(horizon)
                .checksum("NA")
                .type(TrajectoryType.LOAD.name())
                .creationDate(LocalDateTime.now())
                .build();
    }

    private TrajectoryEntity buildAndSaveLoadTrajectory(String area, String horizon, Path trajectoryPath, TrajectoryEntity loadTrajectory) throws IOException {
        List<String> loadsFile = getValidLoadFileNamesWithHorizon(trajectoryPath, area, horizon);
        if (loadsFile.isEmpty()) {

            throw BusinessException.builder()
                    .message("No valid load files found in the trajectory path")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        List<LoadEntity> loadEntities = loadsFile.stream()
                .map(loadFileName -> LoadEntity.builder().fileName(loadFileName).trajectory(loadTrajectory).build())
                .toList();
        loadTrajectory.setLoadEntities(loadEntities);
        loadTrajectory.setLoadArea(area.toUpperCase());
        return trajectoryRepository.save(loadTrajectory);
    }

    /**
     * Processes a trajectory file based on the given type, file name, horizon, and study ID.
     *
     * @param trajectoryType  the type of the trajectory
     * @param trajectoryToUse the name of the trajectory file to use
     * @param horizon         the horizon period in the format yyyy-yyyy
     * @param studyId         the ID of the study
     * @return the processed TrajectoryEntity
     * @throws IOException if an I/O error occurs
     */
    public TrajectoryEntity processTrajectory(TrajectoryType trajectoryType, String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        Path trajectoryFilePath = getTrajectoryFilePath(trajectoryType, trajectoryToUse);

        return switch (trajectoryType) {
            case AREA -> areaFileProcessorService.processAreaFile(trajectoryFilePath, horizon);
            case LINK -> linkFileProcessorService.processLinkFile(trajectoryFilePath, horizon, studyId);
            case THERMAL_CAPACITY ->
                    thermalFileProcessorService.processThermalFile(trajectoryFilePath, horizon, thermalFileProcessorService::buildThermalClusterCapacityValuesList, trajectoryType);
            case THERMAL_PARAMETER ->
                    thermalFileProcessorService.processThermalFile(trajectoryFilePath, horizon, thermalFileProcessorService::buildThermalParameters, trajectoryType);
            case THERMAL_COST ->
                    thermalFileProcessorService.processThermalFile(trajectoryFilePath, horizon, thermalFileProcessorService::buildThermalCosts, trajectoryType);
            default -> throw TechnicalException.builder().message("The provided trajectory type is not supported.").build();
        };
    }

    private Path getTrajectoryFilePath(TrajectoryType trajectoryType, String trajectoryToUse) {
        //build the file path
        Path baseDirectory = Path.of(antaressDataManagerProperties.getNasDirectory())
                .resolve(antaressDataManagerProperties.getTrajectoryFilePath())
                // TODO: Change thermalCapacityArea according to the file tree structure
                .resolve(getDirectoryByTrajectoryType(trajectoryType, ""))
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }
        return baseDirectory.resolve(trajectoryToUse + ".xlsx").normalize();
    }

    public List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameContainsFromDB(TrajectoryType trajectoryType, String horizon, String fileNameContains) {
        return trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(trajectoryType.name(), horizon, fileNameContains);
    }

    /**
     * Finds trajectories by type from the NAS directory.
     *
     * @param trajectoryType the type of the trajectory
     * @return a list of FsTrajectoryDTO representing the trajectories
     */
    public List<FsTrajectoryDTO> findTrajectoriesByType(TrajectoryType trajectoryType, String loadZone, String fileNameContains) {
        String basePath = antaressDataManagerProperties.getNasDirectory();
        String subPath = antaressDataManagerProperties.getTrajectoryFilePath();
        String typePath = trajectoryType.name().toLowerCase();

        if (trajectoryType == TrajectoryType.LOAD && loadZone != null) {
            typePath += "/load_" + loadZone.toUpperCase();
        }

        Path directory = Path.of(basePath).resolve(subPath).resolve(typePath);

        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> isRelevantFile(path, trajectoryType))
                    .map(path -> createFsTrajectoryDTO(path, trajectoryType))
                    .filter(dto -> fileNameMatches(dto, fileNameContains))
                    .collect(Collectors.groupingBy(
                            FsTrajectoryDTO::getFileName,
                            Collectors.maxBy(Comparator.comparing(FsTrajectoryDTO::getLastModifiedDate))
                    ))
                    .values().stream()
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(FsTrajectoryDTO::getLastModifiedDate).reversed())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isRelevantFile(Path path, TrajectoryType trajectoryType) {
        return trajectoryType == TrajectoryType.LOAD ||
                (Files.isRegularFile(path) && isValidTrajectoryFile(path, trajectoryType));
    }

    private boolean fileNameMatches(FsTrajectoryDTO dto, String fileNameContains) {
        return fileNameContains == null ||
                dto.getFileName().toLowerCase().contains(fileNameContains.toLowerCase());
    }


    private FsTrajectoryDTO createFsTrajectoryDTO(Path path, TrajectoryType trajectoryType) {
        try {
            return FsTrajectoryDTO.builder()
                    .fileName(path.getFileName().toString())
                    .lastModifiedDate(Files.getLastModifiedTime(path)
                            .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .type(trajectoryType.name())
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Checks if the file name matches the required prefix for the given TrajectoryType.
     */
    private boolean isValidTrajectoryFile(Path path, TrajectoryType trajectoryType) {
        String fileName = path.getFileName().toString().toLowerCase();

        return switch (trajectoryType) {
            case AREA -> fileName.startsWith(AREAS_PREFIX);
            case LINK -> fileName.startsWith(LINKS_PREFIX);
            default -> true;
        };
    }

    @Override
    public List<TrajectoryDTO> findTrajectoriesByTypeAndStudyId(String trajectoryType, Integer studyId) {
        return trajectoryRepository.findByTypeAndStudyId(trajectoryType, studyId).stream()
                .map(TrajectoryMapper::toTrajectoryDTO)
                .toList();
    }

    private String getDirectoryByTrajectoryType(TrajectoryType trajectoryType, String thermalCapacityArea) {
        return switch (trajectoryType) {
            case AREA -> antaressDataManagerProperties.getAreaDirectory();
            case LINK -> antaressDataManagerProperties.getLinkDirectory();
            case THERMAL_COST -> antaressDataManagerProperties.getThermalCostDirectory();
            case THERMAL_CAPACITY -> Path.of(antaressDataManagerProperties.getThermalCapacityDirectory())
                    .resolve(thermalCapacityArea)
                    .toString();
            case THERMAL_PARAMETER -> antaressDataManagerProperties.getThermalParameterDirectory();
            case LOAD -> antaressDataManagerProperties.getLoadDirectory();
            case MISC ->
                    throw TechnicalException.builder().message("No directory defined for TrajectoryType: " + trajectoryType).build();
            default -> throw TechnicalException.builder().message("Invalid TrajectoryType: " + trajectoryType).build();
        };
    }

    /**
     * Links a trajectory to a study.
     *
     * @param trajectoryId the ID of the trajectory
     * @param studyId      the ID of the study
     * @param type         the type of the trajectory
     * @return the linked TrajectoryEntity
     */
    @Transactional
    public TrajectoryEntity linkTrajectoryToStudy(Integer trajectoryId, Integer studyId, TrajectoryType type) {
        Set<WarningMessageEntity> warningMessageEntities = new HashSet<>(); // Nouvelle instance locale

        StudyEntity study = studyRepository.findById(studyId)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Study not found")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());

        TrajectoryEntity trajectory = trajectoryRepository.findById(trajectoryId)
                .orElseThrow(() ->
                        BusinessException.builder()
                                .message("Trajectory not found")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build());

        // Vérifier si une trajectoire du même type est déjà associée à l'étude
        Optional<StudyTrajectoryEntity> existingLink = study.getStudyTrajectoryEntities().stream()
                .filter(studyTrajectory -> studyTrajectory.getTrajectory().getType().equals(trajectory.getType()))
                .findFirst();

        String userNni = userService.getCurrentUserDetails().getNni();

        // check area link
        checkLinkAreaCoherence(studyId, warningMessageEntities, trajectory, userNni);


        // Supprimer l'ancienne association si elle existe
        existingLink.ifPresent(studyTrajectoryRepository::delete);

        // Créer une nouvelle association
        StudyTrajectoryEntity newStudyTrajectoryEntity = StudyTrajectoryEntity.builder()
                .id(StudyTrajectoryKey.builder()
                        .trajectoryId(trajectoryId)
                        .scenarioId(studyId)
                        .build())
                .studyEntity(study)
                .trajectory(trajectory)
                .build();

        StudyTrajectoryEntity savedStudyTrajectoryEntity = studyTrajectoryRepository.save(newStudyTrajectoryEntity);

        return savedStudyTrajectoryEntity.getTrajectory();
    }

    public void checkLinkAreaCoherence(Integer studyId, Set<WarningMessageEntity> warningMessageEntities, TrajectoryEntity trajectory, String userNni) {
        if (trajectory.getType().equals(TrajectoryType.LINK.name())) {
            checkLinkCoherence(studyId, warningMessageEntities, trajectory, userNni);
        } else if (trajectory.getType().equals(TrajectoryType.AREA.name())) {
            checkAreaCoherence(studyId, warningMessageEntities, trajectory, userNni);
        }
        warningMessageEntities.forEach(warning -> warning.setTrajectory(trajectory));
        warningMessageRepository.saveAll(warningMessageEntities);
    }

    private void checkLinkCoherence(Integer studyId, Set<WarningMessageEntity> warningMessageEntities, TrajectoryEntity trajectory, String userNni) {
        var listLink = trajectory.getLinkEntities();
        List<String> areasSavedForScenario = linkFileProcessorService.findListArea(studyId);
        if (!areasSavedForScenario.isEmpty()) {
            listLink.forEach(link -> linkFileProcessorService.validateLinkAreas(link.getName(), areasSavedForScenario));
            TrajectoryEntity secondTrajectory = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId).stream().findFirst().orElse(null);
            linkFileProcessorService.checkConsistencyTrajectoryLinkAndArea(listLink, areasSavedForScenario, warningMessageEntities, studyId, trajectory.getId(), secondTrajectory, userNni);
        }
    }

    private void checkAreaCoherence(Integer studyId, Set<WarningMessageEntity> warningMessageEntities, TrajectoryEntity trajectory, String userNni) {
        List<String> areasSavedForScenario = trajectory.getAreaConfigEntities().stream()
                .map(area -> area.getArea().getName())
                .toList();
        List<LinkEntity> listLink = linkFileProcessorService.findListLink(studyId);
        if (!listLink.isEmpty()) {
            listLink.forEach(link -> linkFileProcessorService.validateLinkAreas(link.getName(), areasSavedForScenario));
            TrajectoryEntity secondTrajectory = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LINK.name(), studyId).stream().findFirst().orElse(null);
            linkFileProcessorService.checkConsistencyTrajectoryLinkAndArea(listLink, areasSavedForScenario, warningMessageEntities, studyId, trajectory.getId(), secondTrajectory, userNni);
        }
    }

    @Override
    public void unlinkTrajectoryFromStudy(Integer trajectoryId, Integer studyId) {
        studyTrajectoryRepository.findById(StudyTrajectoryKey.builder()
                        .trajectoryId(trajectoryId)
                        .scenarioId(studyId)
                        .build())
                .ifPresentOrElse(studyTrajectoryRepository::delete,
                        () -> {
                            throw BusinessException.builder()
                                    .message("Link not found")
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build();
                        });
    }


    @Override
    public List<TrajectoryDataDTO> getTrajectoryDataByTypeAndId(TrajectoryType trajectoryType, Integer trajectoryId) {
        return switch (trajectoryType) {
            case AREA -> areaConfigRepository.findAreaConfigByTrajectoryId(trajectoryId)
                    .stream()
                    .map(AreaMapper::toAreaTrajectoryDataDTO)
                    .collect(Collectors.toList());

            case LINK -> linkRepository.findLinkEntitiesByTrajectoryIdIs(trajectoryId)
                    .stream()
                    .map(LinkMapper::toLinkTrajectoryDataDTO)
                    .collect(Collectors.toList());

            default -> throw TechnicalException.builder()
                    .message("TrajectoryType {0} is not supported.")
                    .errorMessageArguments(List.of(trajectoryType.name()))
                    .build();
        };
    }
}
