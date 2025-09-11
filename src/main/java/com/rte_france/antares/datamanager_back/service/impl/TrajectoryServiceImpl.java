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
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.util.Utils.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class TrajectoryServiceImpl implements TrajectoryService {

    public static final String OTHER_AREA = "OTHERS";
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

    private final WarningRepository warningRepository;

    private final UserService userService;

    private final LoadRepository loadRepository;

    private static final String AREAS_PREFIX = "areas_";
    private static final String LINKS_PREFIX = "links_";
    private static final String SPECIFIC_PREFIX = "specific_param_";
    private static final String COMMON_PREFIX = "common_param_";
    private static final String CAPACITY_PREFIX = "thermal_";
    private final LoadFileProcessorServiceImpl loadFileProcessorServiceImpl;

    @Transactional
    @Override
    public TrajectoryEntity processLoadTrajectory(String area, String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveLoadTrajectoriesInDb(area, trajectoryToUse, horizon, studyId);
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
        Set<WarningMessageEntity> warningMessageEntities = new HashSet<>();
        if (area == null || trajectoryToUse == null || horizon == null) {
            throw
                    BusinessException.builder()
                            .message("Area, trajectory name, and horizon must not be null")
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();

        }

        if (!area.equals(OTHER_AREA)) {
            checkIfAreaIsLinkedToStudy(studyId, area);
        }

        String userNni = Optional.ofNullable(userService.getCurrentUserDetails())
                .map(UserInfoDto::getNni)
                .orElseThrow(() ->
                        BusinessException.builder()
                                .message("User NNI could not be determined")
                                .httpStatus(HttpStatus.BAD_REQUEST)
                                .build());

        // Build and normalize the trajectory path
        Path trajectoryPath = buildTrajectoryPath(trajectoryToUse);


        // Try to find existing trajectory
        Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                .findFirstByFileNameAndHorizonAndAreaOrderByVersionDesc(trajectoryToUse, horizon, area);

        if (existingTrajectoryOpt.isPresent()) {
            TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
            if ((isSameLoadTrajectory(trajectoryPath, existingTrajectory) && !area.equals(OTHER_AREA))
                    || (area.equals(OTHER_AREA)
                    && isSameVersionOfOtherLoadTrajectory(existingTrajectory, studyId, trajectoryPath, horizon)
            )
            ) {
                throw BusinessException.builder()
                        .message("Trajectory already uploaded")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();

            }

            // Update a version and save new trajectory
            TrajectoryEntity newTrajectory = buildNewLoadTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
            newTrajectory.setVersion(existingTrajectory.getVersion() + 1);
            return buildAndSaveLoadTrajectory(area, horizon, trajectoryPath, newTrajectory, studyId, null);
        }

        // No existing trajectory: create and save new
        TrajectoryEntity newTrajectory = buildNewLoadTrajectory(trajectoryToUse, horizon, trajectoryPath, userNni);
        if (area.equals(OTHER_AREA)) {
            warningMessageEntities = loadFileProcessorServiceImpl.checkForMissingLoadFiles(trajectoryPath, horizon, studyId, userNni, newTrajectory);
        }
        return buildAndSaveLoadTrajectory(area, horizon, trajectoryPath, newTrajectory, studyId, warningMessageEntities);
    }

    private boolean isSameVersionOfOtherLoadTrajectory(TrajectoryEntity existingTrajectory, Integer studyId, Path trajectoryPath, String horizon) {
        List<String> studyAreas = areaRepository.findAllByStudyId(studyId).stream()
                .map(a -> a.getName().toLowerCase())
                .toList();

        log.info("Study Areas: {}", studyAreas);
        Set<String> importedAreas = existingTrajectory.getLoadEntities().stream()
                .map(load -> {
                    String[] parts = load.getFileName().split("_");
                    return parts.length > 1 ? parts[1].toLowerCase() : "";
                })
                .collect(Collectors.toSet());
        log.info("Imported areas: {}", importedAreas);

        for (String area : studyAreas) {
            if (!importedAreas.contains(area)) {
                String fileName = "load_" + area + "_" + horizon + ".txt";
                if (Files.exists(trajectoryPath.resolve(fileName))) {
                    log.info("at least one load is not  missing in the file system for area : {}", area);
                    return false;
                }
            }
        }
        log.info("All areas in the study are already imported in the existing trajectory: {}", existingTrajectory.getFileName());

        return true;
    }

    // Utility method to build trajectory path with checks
    public Path buildTrajectoryPath(String trajectoryToUse) {
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
                .resolve(trajectoryToUse)
                .normalize();
    }


    private TrajectoryEntity buildNewLoadTrajectory(String trajectoryToUse, String horizon, Path trajectoryPath, String userNni) throws IOException {
        //build new trajectory
        return TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .fileSize(Files.size(trajectoryPath))
                .createdBy(userNni)
                .version(1)
                .lastModificationContentDate(Files.getLastModifiedTime(trajectoryPath).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .horizon(horizon)
                .checksum("NA")
                .type(TrajectoryType.LOAD.name())
                .creationDate(LocalDateTime.now())
                .build();
    }

    private TrajectoryEntity buildAndSaveLoadTrajectory(String area, String horizon, Path trajectoryPath, TrajectoryEntity loadTrajectory, Integer studyId, Set<WarningMessageEntity> warningMessageEntities) throws IOException {
        List<String> listCustomLoadFilesAlreadyChoosed = new ArrayList<>();
        if (area.equals(OTHER_AREA)) {
            listCustomLoadFilesAlreadyChoosed = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LOAD.name(), studyId)
                    .stream()
                    .map(TrajectoryEntity::getArea)
                    .filter(loadArea -> !loadArea.equals(OTHER_AREA))
                    .map(String::toLowerCase)
                    .toList();
        }
        List<String> areaWithStudy = areaRepository.findAllByStudyId(studyId).stream().map(areaStudy -> areaStudy.getName().toLowerCase()).toList();

        List<String> loadsFile = getValidLoadFileNamesWithHorizon(trajectoryPath, area, horizon, listCustomLoadFilesAlreadyChoosed, areaWithStudy);
        if (loadsFile.isEmpty()) {

            throw BusinessException.builder()
                    .errorMessageArguments(List.of(area, horizon))
                    .message("No valid load files found in the trajectory path for area: {0} and horizon: {1}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        Set<LoadEntity> loadEntities = new HashSet<>();
        for (String loadFileName : loadsFile) {
            // Vérification d'existence par nom de fichier et nom de trajectoire
            Optional<LoadEntity> existingLoad = loadRepository.findByFileNameAndTrajectoryFileName(loadFileName, loadTrajectory.getFileName());
            LoadEntity loadEntity;
            loadEntity = existingLoad.orElseGet(() -> {
                String areaName = extractAreaFromFileName(loadFileName);
                return LoadEntity.builder()
                        .fileName(loadFileName)
                        .area(areaName)
                        .build();
            });

            loadEntity.addTrajectoryEntity(loadTrajectory);
            loadEntities.add(loadEntity);
        }
        loadTrajectory.setLoadEntities(loadEntities);
        loadTrajectory.setArea(area.toUpperCase());
        loadTrajectory.setWarningMessages(warningMessageEntities);
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
        Path trajectoryFilePath = getTrajectoryFilePath(trajectoryType, trajectoryToUse, "");

        return switch (trajectoryType) {
            case AREA -> areaFileProcessorService.processAreaFile(trajectoryFilePath, horizon);
            case LINK -> linkFileProcessorService.processLinkFile(trajectoryFilePath, horizon, studyId);
            default ->
                    throw TechnicalException.builder().message("The provided trajectory type is not supported.").build();
        };
    }


    /**
     * Processes a trajectory file based on the given type, file name, horizon, and study ID.
     *
     * @param trajectoryToUse the name of the trajectory file to use
     * @param horizon         the horizon period in the format yyyy-yyyy
     * @param studyId         the ID of the study
     * @return the processed TrajectoryEntity
     * @throws IOException if an I/O error occurs
     */
    public TrajectoryEntity processThermalCapacityTrajectory(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String area, String technology) throws IOException {
        if (trajectoryToUse == null || !trajectoryToUse.toLowerCase().startsWith("thermal_")) {
            throw BusinessException.builder()
                    .message("The trajectory file name must start with 'thermal_'")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        Path trajectoryFilePath = getTrajectoryFilePath(TrajectoryType.THERMAL_CAPACITY, trajectoryToUse, area);
        ThermalClusterCapacityDto thermalClusterCapacityDto = thermalFileProcessorService.buildThermalClusterCapacityValuesList(trajectoryFilePath, horizon, isCivilYear,area, technology,studyId);
        if (CollectionUtils.isEmpty(thermalClusterCapacityDto.getThermalClusterCapacities())) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryToUse, area, horizon))
                    .message("No valid thermal cluster capacity found in the trajectory {0} for area: {1} and horizon: {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return   thermalFileProcessorService.processThermalCapacityFile(trajectoryFilePath, horizon, thermalClusterCapacityDto, TrajectoryType.THERMAL_CAPACITY, area, technology);

    }

    @Override
    public TrajectoryEntity processThermalCommonParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        Path trajectoryFilePath = getTrajectoryFilePath(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, trajectoryToUse,"");
        var params = thermalFileProcessorService.buildThermalCommonParameterValuesList(trajectoryFilePath, horizon, true);
        if (CollectionUtils.isEmpty(params)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryToUse, horizon))
                    .message("No valid thermal common parameter found in the trajectory {0} for area: {1} and horizon: {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return thermalFileProcessorService.processThermalCommonParameterFile(trajectoryFilePath, horizon, params, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER);
    }

    private void checkIfAreaIsLinkedToStudy(Integer studyId, String area) {
        areaRepository.findAreaByNameAndStudyId(area, studyId).orElseThrow(() ->
                BusinessException.builder()
                        .message("Area not found for studyId: {0} ")
                        .errorMessageArguments(List.of(studyId.toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());
    }

    public Path getTrajectoryFilePath(TrajectoryType trajectoryType, String trajectoryToUse, String area) throws IOException {
        //build the file path
        Path baseDirectory = Path.of(antaressDataManagerProperties.getNasDirectory())
                .resolve(antaressDataManagerProperties.getTrajectoryFilePath())
                .resolve(getDirectoryByTrajectoryType(trajectoryType, area))
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        //download the file
        Path trajectoryFilePath = baseDirectory.resolve(trajectoryToUse + ".xlsx").normalize();
        if (!trajectoryFilePath.startsWith(baseDirectory)) {
            throw new IOException("Path is outside of the target directory");
        }
        return trajectoryFilePath;
    }

    public List<TrajectoryEntity> findTrajectoriesByTypeAndFileNameContainsFromDB(TrajectoryType trajectoryType, String horizon, String fileNameContains, String area, String technology) {
        return trajectoryRepository.findTrajectoriesFileNameByTypeAndHorizonAndFileNameContains(trajectoryType.name(), horizon, fileNameContains, area, technology);
    }

    /**
     * Finds trajectories by type from the NAS directory.
     *
     * @param trajectoryType the type of the trajectory
     * @return a list of FsTrajectoryDTO representing the trajectories
     */
    public List<FsTrajectoryDTO> findTrajectoriesByType(TrajectoryType trajectoryType, String area, String fileNameContains) throws TechnicalException {
        Path directory = normalizeAndValidateDirectory(trajectoryType, area);
        try (var stream = Files.list(directory.normalize())) {
            return stream
                    .filter(path -> isRelevantFile(path, trajectoryType))
                    .filter(path -> switch (trajectoryType) {
                        case THERMAL_CAPACITY ->
                                path.getFileName().toString().startsWith(CAPACITY_PREFIX);
                        case THERMAL_TECHNICAL_SPECIFIC_PARAMETER ->
                                path.getFileName().toString().startsWith(SPECIFIC_PREFIX);
                        case THERMAL_TECHNICAL_COMMON_PARAMETER -> path.getFileName().toString().startsWith(COMMON_PREFIX);
                        default -> true;
                    })
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


    private Path normalizeAndValidateDirectory(TrajectoryType trajectoryType, String area) {
        String basePath = antaressDataManagerProperties.getNasDirectory();
        String subPath = antaressDataManagerProperties.getTrajectoryFilePath();
        Path baseDirectory = Path.of(basePath).resolve(subPath)
                .resolve(getDirectoryByTrajectoryType(trajectoryType, area))
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        return baseDirectory.normalize();
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
        boolean isXlsx = fileName.endsWith(".xlsx");

        return switch (trajectoryType) {
            case AREA -> isXlsx && fileName.startsWith(AREAS_PREFIX);
            case LINK -> isXlsx && fileName.startsWith(LINKS_PREFIX);
            default -> isXlsx; // for all other types, only accept .xlsx files
        };

    }

    @Override
    public List<TrajectoryDTO> findTrajectoriesByTypeAndStudyId(String trajectoryType, Integer studyId) {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findByTypeAndStudyId(trajectoryType, studyId).stream()
                .peek(trajectory ->
                        trajectory.setWarningMessages(filterWarningMessages(studyId, trajectory.getWarningMessages()))).toList();
        return TrajectoryMapper.toTrajectoryDtos(trajectoryEntities);
    }

    private Set<WarningMessageEntity> filterWarningMessages(Integer
                                                                    studyId, Set<WarningMessageEntity> warningMessages) {
        if (warningMessages == null) {
            return new LinkedHashSet<>();
        }
        return warningMessages.stream()
                .filter(warning -> warning.getStudy().getId().equals(studyId) && isStudyTrajectoryExistById(studyId, warning))
                .sorted(Comparator
                        .comparing(WarningMessageEntity::getIsAck) // ack = true d'abord
                        .thenComparing(WarningMessageEntity::getCreationDate, Comparator.reverseOrder()) // tri décroissant par date
                )
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean isStudyTrajectoryExistById(Integer studyId, WarningMessageEntity warning) {
        return warning.getSecondTrajectory() == null || studyTrajectoryRepository.findById(StudyTrajectoryKey.builder()
                        .trajectoryId(warning.getSecondTrajectory().getId())
                        .scenarioId(studyId)
                        .build())
                .isPresent();
    }


    public String getDirectoryByTrajectoryType(TrajectoryType trajectoryType, String area) {
        return switch (trajectoryType) {
            case AREA -> antaressDataManagerProperties.getAreaDirectory();
            case LINK -> antaressDataManagerProperties.getLinkDirectory();
            case LOAD -> antaressDataManagerProperties.getLoadDirectory();
            case THERMAL_CAPACITY ->
                    area.equals("FR") ? Path.of(antaressDataManagerProperties.getThermalCapacityDirectory())
                            .resolve(area)
                            .toString() : Path.of(antaressDataManagerProperties.getThermalCapacityDirectory())
                            .toString();
            case THERMAL_TECHNICAL_SPECIFIC_PARAMETER, THERMAL_TECHNICAL_COMMON_PARAMETER ->
                    antaressDataManagerProperties.getThermalParameterDirectory();
            case THERMAL_ECONOMIC_COST_PARAMETER -> antaressDataManagerProperties.getThermalCostDirectory();
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
    public TrajectoryEntity linkTrajectoryToStudy(Integer trajectoryId, Integer studyId, TrajectoryType type) throws
            IOException {
        Set<WarningMessageEntity> warningMessageEntities = new HashSet<>();

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

        Optional<StudyTrajectoryEntity> existingLink = Optional.empty();
        if ((TrajectoryType.AREA.equals(type) || TrajectoryType.LINK.equals(type)) && study.getStudyTrajectoryEntities() != null) {
            existingLink = study.getStudyTrajectoryEntities().stream()
                    .filter(studyTrajectory -> studyTrajectory.getTrajectory() != null
                            && studyTrajectory.getTrajectory().getType() != null
                            && studyTrajectory.getTrajectory().getType().equals(trajectory.getType()))
                    .findFirst();
        }

        String userNni = userService.getCurrentUserDetails().getNni();


        checkTrajectoryCoherence(studyId, warningMessageEntities, trajectory, userNni);

        existingLink.ifPresent(studyTrajectoryRepository::delete);


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

    public void checkTrajectoryCoherence(Integer
                                                 studyId, Set<WarningMessageEntity> warningMessages, TrajectoryEntity trajectory, String userNni) throws
            IOException {
        switch (trajectory.getType()) {
            case "LINK" -> checkLinkCoherence(studyId, warningMessages, trajectory, userNni);
            case "AREA" -> checkAreaCoherence(studyId, warningMessages, trajectory, userNni);
            default -> {
                if (OTHER_AREA.equals(trajectory.getArea()) && TrajectoryType.LOAD.name().equals(trajectory.getType())) {
                    warningMessages = loadFileProcessorService.checkForMissingLoadByAreaFromDb(
                            trajectory.getHorizon(), studyId, userNni, trajectory);
                }
            }
        }
        warningMessages.forEach(warning -> warning.setTrajectory(trajectory));
        warningRepository.saveAll(warningMessages);
    }

    public void checkLinkCoherence(Integer
                                           studyId, Set<WarningMessageEntity> warningMessageEntities, TrajectoryEntity trajectory, String userNni) {
        var listLink = trajectory.getLinkEntities();
        List<String> areasSavedForScenario = linkFileProcessorService.findListArea(studyId);
        if (!areasSavedForScenario.isEmpty()) {

            Set<String> allMissingAreas = new HashSet<>();
            for (LinkEntity link : listLink) {
                String[] areas = link.getName().split("-");
                for (String area : areas) {
                    if (areasSavedForScenario.stream()
                            .noneMatch(existingArea -> existingArea.equalsIgnoreCase(area))) {
                        allMissingAreas.add(area);
                    }
                }
            }

            if (!allMissingAreas.isEmpty()) {
                String missingAreasString = String.join(", ", allMissingAreas);
                throw BusinessException.builder()
                        .message("Areas {0} in LINKS file is not present in AREA trajectory")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .errorMessageArguments(List.of(missingAreasString))
                        .build();
            }

            TrajectoryEntity secondTrajectory = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA.name(), studyId)
                    .stream().findFirst().orElse(null);
            linkFileProcessorService.checkConsistencyTrajectoryLinkAndArea(listLink, areasSavedForScenario,
                    warningMessageEntities, studyId, trajectory.getId(), secondTrajectory, userNni);
        }
    }

    private void checkAreaCoherence(Integer
                                            studyId, Set<WarningMessageEntity> warningMessageEntities, TrajectoryEntity trajectory, String userNni) {
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
        validateAreaTrajectoryDeletion(trajectoryId, studyId);

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

    private List<TrajectoryEntity> getOtherTrajectoriesLinkedToStudy(Integer studyId, Integer excludedTrajectoryId) {
        return trajectoryRepository.findByTypeAndStudyId(null, studyId)
                .stream()
                .filter(t -> !t.getId().equals(excludedTrajectoryId))
                .toList();
    }


    private void validateAreaTrajectoryDeletion(Integer trajectoryId, Integer studyId) {
        var trajectory = trajectoryRepository.findById(trajectoryId)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Trajectory not found")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());

        if (trajectory.getType().equals(TrajectoryType.AREA.name())) {
            var others = getOtherTrajectoriesLinkedToStudy(studyId, trajectoryId);
            if (!others.isEmpty()) {
                throw BusinessException.builder()
                        .message("Other trajectories are linked. Confirmation required")
                        .httpStatus(HttpStatus.CONFLICT)
                        .build();
            }
        }
    }

    @Override
    @Transactional
    public void unlinkBatchTrajectoriesFromStudy(Integer studyId, List<Integer> trajectoryIds) {
        Objects.requireNonNull(trajectoryIds);

        var ids = trajectoryIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw BusinessException.builder()
                    .message("trajectoryIds must not be empty")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        var deleted = studyTrajectoryRepository.deleteByStudyIdAndTrajectoryIds(studyId, ids);
        if (deleted != ids.size()) {
            throw BusinessException.builder()
                    .message("Batch detach of trajectories {0} failed")
                    .errorMessageArguments(List.of(ids.toString()))
                    .httpStatus(HttpStatus.CONFLICT)
                    .build();
        }
    }

    @Override
    @Transactional
    public void unlinkAllTrajectoriesFromStudy(Integer studyId) {
        var links = studyTrajectoryRepository.findById_ScenarioId(studyId);
        if (!links.isEmpty()) {
            studyTrajectoryRepository.deleteAll(links);
        } else {
            throw BusinessException.builder()
                    .message("No links found")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
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

    @Override
    public Map<String, Integer> countWarningMessage(Integer studyId) {
        return trajectoryRepository.findByTypeAndStudyId(null, studyId).stream()
                .peek(trajectory ->
                        trajectory.setWarningMessages(filterWarningMessages(studyId, trajectory.getWarningMessages()))).toList()
                .stream()
                .collect(Collectors.groupingBy(
                        TrajectoryEntity::getType,
                        Collectors.summingInt(trajectory -> trajectory.getWarningMessages() != null ? trajectory.getWarningMessages().size() : 0)
                ));
    }
}