package com.rte_france.antares.datamanager_back.service.common.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.*;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.AreaMapper;
import com.rte_france.antares.datamanager_back.mapper.LinkMapper;
import com.rte_france.antares.datamanager_back.mapper.StStorageMapper;
import com.rte_france.antares.datamanager_back.mapper.TrajectoryMapper;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.area_link.AreaFileProcessorService;
import com.rte_france.antares.datamanager_back.service.area_link.LinkFileProcessorService;
import com.rte_france.antares.datamanager_back.service.common.DefaultConfigService;
import com.rte_france.antares.datamanager_back.service.common.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.load.LoadFileProcessorService;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.misc.impl.MiscFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.thermal.*;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.RES_CAPACITY;
import static com.rte_france.antares.datamanager_back.dto.TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER;
import static com.rte_france.antares.datamanager_back.service.misc.impl.MiscFileProcessorServiceImpl.readHeaderAreas;
import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalEconomicServiceImpl.SHEET_CO2;
import static com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalEconomicServiceImpl.SHEET_ENR;
import static com.rte_france.antares.datamanager_back.util.Utils.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class TrajectoryServiceImpl implements TrajectoryService {

    public static final String OTHER_AREA = "OTHERS";
    private final AreaFileProcessorService areaFileProcessorService;

    private final LinkFileProcessorService linkFileProcessorService;

    private final AntaresDataManagerProperties antaresDataManagerProperties;

    private final TrajectoryRepository trajectoryRepository;

    private final ThermalFileProcessorService thermalFileProcessorService;

    private final ThermalEconomicService thermalEconomicService;

    private final ThermalControlService thermalControlService;

    private final ThermalSpecificFileProcessorService thermalSpecificProcessorService;


    private final ThermalEconomicCostAndRateService thermalEconomicCostAndRateService;

    private final LoadFileProcessorService loadFileProcessorService;

    private final StudyRepository studyRepository;

    private final StudyTrajectoryRepository studyTrajectoryRepository;

    private final AreaConfigRepository areaConfigRepository;

    private final AreaRepository areaRepository;

    private final LinkRepository linkRepository;

    private final WarningRepository warningRepository;

    private final UserService userService;

    private final LoadRepository loadRepository;

    private final StStorageRepository stStorageRepository;

    private final ThermalParamModulationService thermalParamModulationService;

    private final MiscClusterCapacityRepository miscClusterCapacityRepository;

    private final DefaultConfigService defaultConfigService;

    private static final String AREAS_PREFIX = "areas_";
    private static final String LINKS_PREFIX = "links_";
    private static final String SPECIFIC_PREFIX = "specific_param_";
    private static final String COMMON_PREFIX = "common_param_";
    private static final String CAPACITY_PREFIX = "thermal_";
    private static final String ECONOMIC_COST_PREFIX = "costs_";
    private static final String ECONOMIC_PREFIX = "economic_param_";
    private static final String STS_PREFIX = "cluster_";
    private static final String DSR_PREFIX = "cluster_dsr_";
    private static final String DSR_CAPACITY_PREFIX = "cm_";
    private static final String MISC_CAPACITY_PREFIX = "installedmisc_";
    public static final String RES_CAPACITY_PREFIX = "installedres_";
    public static final String RES_ZONAL_DISTRIBUTION_PREFIX = "repartition_zonale_";
    public static final String RES_TECHNOLOGY_DISTRIBUTION_PREFIX = "repartition_techno_";
    private final LoadFileProcessorServiceImpl loadFileProcessorServiceImpl;

    @Transactional
    @Override
    public TrajectoryEntity processLoadTrajectory(String area, String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        return saveLoadTrajectoriesInDb(area, trajectoryToUse, horizon, studyId);
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
                    .<TrajectoryDataDTO>map(AreaMapper::toAreaTrajectoryDataDTO)
                    .toList();

            case LINK -> linkRepository.findLinkEntitiesByTrajectoryIdIs(trajectoryId)
                    .stream()
                    .<TrajectoryDataDTO>map(LinkMapper::toLinkTrajectoryDataDTO)
                    .toList();

            case STS -> stStorageRepository.findStStorageEntitiesByTrajectoryId(trajectoryId)
                    .stream()
                    .<TrajectoryDataDTO>map(StStorageMapper::toStStorageTrajectoryDataDTO)
                    .toList();

            default -> throw TechnicalException.builder()
                    .message("TrajectoryType {0} is not supported.")
                    .errorMessageArguments(List.of(trajectoryType.name()))
                    .build();
        };
    }

    @Override
    public Map<String, Integer> countWarningMessage(Integer studyId) {
        return trajectoryRepository.findByTypeAndStudyId(null, studyId).stream()
                .map(trajectory -> {
                    trajectory.setWarningMessages(filterWarningMessages(studyId, trajectory.getWarningMessages()));
                    return trajectory;
                })
                .collect(Collectors.groupingBy(
                        TrajectoryEntity::getType,
                        Collectors.summingInt(trajectory -> trajectory.getWarningMessages() != null ? trajectory.getWarningMessages().size() : 0)
                ));
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
    @Transactional
    public TrajectoryEntity processThermalCapacityTrajectory(String trajectoryToUse, String horizon, Integer studyId, boolean isCivilYear, String area, String technology) throws IOException {
        if (trajectoryToUse == null || !trajectoryToUse.toLowerCase().startsWith(CAPACITY_PREFIX)) {
            throw BusinessException.builder()
                    .message("The trajectory file name must start with '" + CAPACITY_PREFIX + "'")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        Path trajectoryFilePath = getTrajectoryFilePath(TrajectoryType.THERMAL_CAPACITY, trajectoryToUse, area);
        ThermalClusterCapacityDto thermalClusterCapacityDto = thermalFileProcessorService.buildThermalClusterCapacityValuesList(trajectoryFilePath, horizon, isCivilYear, area, technology, studyId);
        if (CollectionUtils.isEmpty(thermalClusterCapacityDto.getThermalClusterCapacities())) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryToUse, area, horizon))
                    .message("No valid thermal cluster capacity found in the trajectory {0} for area: {1} and horizon: {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return thermalFileProcessorService.processThermalCapacityFile(trajectoryFilePath, horizon, thermalClusterCapacityDto, TrajectoryType.THERMAL_CAPACITY, area, technology);

    }

    @Transactional
    @Override
    public TrajectoryEntity processThermalCommonParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        Path trajectoryFilePath = getTrajectoryFilePath(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, trajectoryToUse, "");
        var params = thermalFileProcessorService.buildThermalCommonParameterValuesList(trajectoryFilePath, horizon, studyId);
        if (CollectionUtils.isEmpty(params)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryToUse, horizon))
                    .message("No valid thermal common parameter found in the trajectory {0} for area: {1} and horizon: {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return thermalFileProcessorService.processThermalCommonParameterFile(trajectoryFilePath, horizon, params, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER);
    }

    /**
     * Processes a thermal-specific parameter trajectory by validating the input data, filtering parameters,
     * and building the corresponding trajectory entity.
     * <p>
     * This method validates the horizon data for the provided trajectory file, filters out invalid or
     * unassociated parameters based on the study areas, and generates a trajectory entity. If required, a
     * warning message is also created to flag missing areas from the trajectory file.
     *
     * @param trajectoryName the name of the trajectory to be processed
     * @param horizon        the specific horizon for which this trajectory applies
     * @param area           the area identifier for which the trajectory pertains
     * @param studyId        the identifier of the study to which this trajectory is linked
     * @return the created and saved trajectory entity
     * @throws IOException if an issue occurs while accessing the trajectory file
     */
    @Transactional
    @Override
    public TrajectoryEntity processThermalSpecificParameterTrajectory(String trajectoryName, String horizon, String area, Integer studyId) throws IOException {
        Path trajectoryFilePath = getTrajectoryFilePath(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER, trajectoryName, "");
        var params = thermalSpecificProcessorService.buildThermalSpecificParameterValueList(trajectoryName, trajectoryFilePath, horizon, area, studyId);
        if (CollectionUtils.isEmpty(params)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryName, horizon))
                    .message("No valid thermal specific parameter found in the trajectory {0} for area: {1} and horizon: {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        // Filter out rows whose area is not present in the study AREA trajectory
        List<String> studyAreas = areaRepository.findAllByStudyId(studyId)
                .stream()
                .map(a -> a.getName().toUpperCase())
                .toList();
        Set<String> fileAreas = params.stream()
                .map(p -> Optional.ofNullable(p.getNode()).orElse("").toUpperCase())
                .collect(Collectors.toSet());

        // The selected area must be present in the file's 'node' column, except when area equals OTHERS
        if (area != null && !area.isBlank() && !OTHER_AREA.equals(area) && !fileAreas.contains(area.toUpperCase())) {
            throw BusinessException.builder()
                    .message("Selected area " + area + " is not present in the 'node' column of THERMAL Specific Param trajectory " + trajectoryName)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        String createdBy = userService.getCurrentUserDetails() != null ? userService.getCurrentUserDetails().getNni() : "UNKNOWN__USER";

        TrajectoryEntity trajectory = buildTrajectory(trajectoryFilePath, 0, horizon, createdBy, TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER, area, null, null);

        Optional<TrajectoryEntity> existingOpt = trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaAndTechnologyIgnoreCaseOrderByVersionDesc(
                trajectory.getFileName(),
                TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER.name(),
                horizon,
                area,
                null
        );
        if (existingOpt.isPresent() && checkTrajectoryVersion(trajectoryFilePath, existingOpt.get())) {
            trajectory.setVersion(existingOpt.get().getVersion() + 1);
        }

        List<String> missingAreas = studyAreas.stream()
                .filter(sa -> !fileAreas.contains(sa))
                .toList();
        if (!missingAreas.isEmpty()) {
            String message = "Area(s) " + String.join(", ", missingAreas)
                    + " in AREA trajectory is not present in THERMAL Specific Param trajectory "
                    + trajectoryName;
            WarningMessageEntity warning = WarningMessageEntity.builder()
                    .warningContent(message)
                    .warningLevel(WarningLevel.WARNING_LEVEL)
                    .warningCode(WarningCode.THERMAL_SPECIFIC_PARAM_MISSING_AREAS)
                    .study(studyRepository.findById(studyId)
                            .orElseThrow(() -> BusinessException.builder()
                                    .message("Study not found with id: " + studyId)
                                    .httpStatus(HttpStatus.NOT_FOUND)
                                    .build()))
                    .creationDate(LocalDateTime.now())
                    .createdBy(createdBy)
                    .isAck(false)
                    .trajectory(trajectory)
                    .build();
            trajectory.setWarningMessages(Set.of(warning));
        }

        return thermalSpecificProcessorService.saveThermalSpecificTrajectory(trajectory, params, TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER);

    }

    @Override
    public TrajectoryEntity processThermalModulationParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        Path trajectoryFilePath = buildTrajectoryPath(trajectoryToUse, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER);

        String cmFileName = "CM_" + trajectoryToUse + "_" + horizon + ".csv";
        String mrFileName = "MR_" + trajectoryToUse + "_" + horizon + ".csv";

        Path cmFile = findFile(trajectoryFilePath, cmFileName).orElse(null);
        Path mrFile = findFile(trajectoryFilePath, mrFileName).orElse(null);

        if (cmFile == null && mrFile == null) {
            throw BusinessException.builder()
                    .message("No CM and MR trajectories found in trajectory {0} for horizon {1}")
                    .errorMessageArguments(List.of(trajectoryToUse, horizon))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (cmFile == null) {
            throw BusinessException.builder()
                    .message("Missing Cost Modulation file in trajectory {0} for horizon {1}")
                    .errorMessageArguments(List.of(trajectoryToUse, horizon))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        if (mrFile == null) {
            throw BusinessException.builder()
                    .message("Missing Must Run file in trajectory {0} for horizon {1}")
                    .errorMessageArguments(List.of(trajectoryToUse, horizon))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        List<ThermalModulationParameterEntity> thermalModulationParameters = new ArrayList<>();

        thermalParamModulationService.processThermalModulationSingleFile(trajectoryToUse, horizon,
                studyId, trajectoryFilePath.resolve(cmFileName), cmFileName, thermalModulationParameters, cmFile, "CM");

        thermalParamModulationService.processThermalModulationSingleFile(trajectoryToUse, horizon,
                studyId, trajectoryFilePath.resolve(mrFileName), mrFileName, thermalModulationParameters, mrFile, "MR");

        return thermalParamModulationService.processThermalModulationParameterFile(trajectoryFilePath, horizon, thermalModulationParameters, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER);
    }

    @Override
    public TrajectoryEntity processThermalEconomicCostTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        Path trajectoryFilePath = getTrajectoryFilePath(TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER, trajectoryToUse, "");
        String oneYearHorizon = horizon.split("-")[1];
        thermalControlService.verifyCostsTrajectory(oneYearHorizon, trajectoryFilePath, trajectoryToUse, studyId);
        var thermalCostTypeEntities = thermalEconomicCostAndRateService.buildThermalEconomicCostValueList(trajectoryToUse, trajectoryFilePath, oneYearHorizon, studyId);
        var thermalRateEntities = thermalEconomicCostAndRateService.buildThermalEconomicRateValueList(trajectoryToUse, trajectoryFilePath, oneYearHorizon, studyId);
        if (CollectionUtils.isEmpty(thermalCostTypeEntities)) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryToUse, horizon))
                    .message("No valid thermal common parameter found in the trajectory {0} for area: {1} and horizon: {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return thermalFileProcessorService.processThermalEconomicCostsAndRatesFile(trajectoryFilePath, horizon, thermalCostTypeEntities, thermalRateEntities, TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER);
    }

    @Override
    public TrajectoryEntity processThermalEconomicParameterTrajectory(String trajectoryToUse, String horizon, Integer studyId) throws IOException {
        Path trajectoryFilePath = getTrajectoryFilePath(TrajectoryType.THERMAL_ECONOMIC_PARAMETER, trajectoryToUse, "");
        final String fileName = trajectoryFilePath.getFileName().toString();

        try (InputStream inputStream = Files.newInputStream(trajectoryFilePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheetEnr = findHorizonSheet(workbook, SHEET_ENR);
            Sheet sheetCo2 = findHorizonSheet(workbook, SHEET_CO2);
            verifyExistingEconomicSheet(trajectoryToUse, sheetCo2, sheetEnr);

            var economicsCo2Param = thermalEconomicService.buildThermalEconomicCo2ParameterValuesList(fileName, horizon, studyId, sheetCo2);
            var economicsEnerContentParam = thermalEconomicService.buildThermalEconomicEnerContentParameterValuesList(fileName, horizon, studyId, sheetEnr);

            if (CollectionUtils.isEmpty(economicsEnerContentParam)) {
                throw BusinessException.builder()
                        .errorMessageArguments(List.of(trajectoryToUse))
                        .message("No data in THERMAL Economic trajectory {0} in ener_content tab ")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            return thermalEconomicService.processThermalEconomicParameterFile(trajectoryFilePath, horizon, economicsCo2Param, economicsEnerContentParam, TrajectoryType.THERMAL_ECONOMIC_PARAMETER);
        }
    }

    public static void verifyExistingEconomicSheet(String trajectoryToUse, Sheet sheetCo2, Sheet sheetEnr) {
        if (sheetCo2 == null && sheetEnr == null) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryToUse))
                    .message("Missing CO2_emissions /ener_content data in trajectory {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (sheetCo2 == null) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryToUse))
                    .message("Missing CO2_emissions data in trajectory {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (sheetEnr == null) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(trajectoryToUse))
                    .message("Missing ener_content data in trajectory {0}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();


        }
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
    public List<FsTrajectoryDTO> findTrajectoriesByType(TrajectoryType trajectoryType, String area, String technology, String fileNameContains) throws TechnicalException, IOException {
        Path directory = normalizeAndValidateDirectory(trajectoryType, area, technology);
        try (var stream = Files.list(directory.normalize())) {
            return stream
                    .filter(path -> (isDirectoryTrajectory(path, trajectoryType, area) ||
                            (isRelevantFile(path, trajectoryType) && matchesPrefix(path, trajectoryType, technology, area))))
                    .map(path -> getFsTrajectoryDTO(trajectoryType, path))
                    .filter(dto -> fileNameMatches(dto, fileNameContains))
                    .collect(Collectors.groupingBy(
                            FsTrajectoryDTO::getFileName,
                            Collectors.maxBy(Comparator.comparing(FsTrajectoryDTO::getLastModifiedDate))
                    ))
                    .values().stream()
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(FsTrajectoryDTO::getLastModifiedDate).reversed())
                    .toList();

        } 
        catch (IOException e) {
           throw TechnicalException.builder().message("Could not find directory: " + directory).build();
        }
    }

    private boolean matchesPrefix(Path path, TrajectoryType trajectoryType, String technology, String area) {
        String fileName = path.getFileName().toString().toLowerCase();
        String technologyPrefix = (technology == null ? "" : technology.toLowerCase() + "_");
        return switch (trajectoryType) {
            case THERMAL_CAPACITY -> fileName.startsWith(CAPACITY_PREFIX);
            case THERMAL_TECHNICAL_SPECIFIC_PARAMETER -> fileName.startsWith(SPECIFIC_PREFIX);
            case THERMAL_TECHNICAL_COMMON_PARAMETER -> fileName.startsWith(COMMON_PREFIX);
            case LOAD, MISC_LOAD, RES_LOAD, THERMAL_TECHNICAL_MODULATION_PARAMETER -> Files.isDirectory(path);
            case THERMAL_ECONOMIC_COST_PARAMETER -> fileName.startsWith(ECONOMIC_COST_PREFIX);
            case THERMAL_ECONOMIC_PARAMETER -> fileName.startsWith(ECONOMIC_PREFIX);
            case DSR -> fileName.startsWith(DSR_PREFIX);
            case DSR_CAPACITY_MODULATION -> fileName.startsWith(DSR_CAPACITY_PREFIX);
            case STS -> fileName.matches("^" + Pattern.quote(STS_PREFIX) + "(?i:" + Pattern.quote(technology) + ")_.*");
            case MISC_CAPACITY -> fileName.startsWith(MISC_CAPACITY_PREFIX);
            case RES_CAPACITY -> isDefaultArea(area) ? Files.isDirectory(path) : fileName.startsWith(RES_CAPACITY_PREFIX);
            case RES_ZONAL_DISTRIBUTION -> fileName.startsWith(RES_ZONAL_DISTRIBUTION_PREFIX);
            case RES_TECHNOLOGY_DISTRIBUTION -> fileName.startsWith(RES_TECHNOLOGY_DISTRIBUTION_PREFIX + technologyPrefix);
            default -> true;
        };
    }


    @Override
    public List<TrajectoryDTO> findTrajectoriesByTypeAndStudyId(String trajectoryType, Integer studyId) {
        List<TrajectoryEntity> trajectoryEntities = trajectoryRepository.findByTypeAndStudyId(trajectoryType, studyId).stream()
                .map(trajectory -> {
                    trajectory.setWarningMessages(filterWarningMessages(studyId, trajectory.getWarningMessages()));
                    return trajectory;
                })
                .toList();
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
                        .comparing(WarningMessageEntity::getIsAck) // ack = true
                        .thenComparing(WarningMessageEntity::getCreationDate, Comparator.reverseOrder()) // decreasing order by date
                )
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }


    @Transactional
    public TrajectoryEntity linkTrajectoryToStudy(Integer trajectoryId, Integer studyId, TrajectoryType type) throws IOException {
        Set<WarningMessageEntity> warningMessageEntities = new HashSet<>();

        StudyEntity study = studyRepository.findById(studyId)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Study not found")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());

        TrajectoryEntity trajectory = trajectoryRepository.findById(trajectoryId)
                .orElseThrow(() -> BusinessException.builder()
                        .message("Trajectory not found")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());

        Set<TrajectoryType> singleLinkTypes = Set.of(
                TrajectoryType.AREA,
                TrajectoryType.LINK,
                THERMAL_TECHNICAL_MODULATION_PARAMETER,
                TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER,
                TrajectoryType.THERMAL_ECONOMIC_PARAMETER,
                TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER,
                TrajectoryType.DSR_CAPACITY_MODULATION
        );

        Optional<StudyTrajectoryEntity> existingLink = Optional.empty();
        if (singleLinkTypes.contains(type) && study.getStudyTrajectoryEntities() != null) {
            existingLink = study.getStudyTrajectoryEntities().stream()
                    .filter(st -> st.getTrajectory() != null
                            && st.getTrajectory().getType() != null
                            && st.getTrajectory().getType().equals(trajectory.getType()))
                    .findFirst();
        }

        String userNni = userService.getCurrentUserDetails().getNni();

        Set<TrajectoryType> supportedTypes = Set.of(
                TrajectoryType.AREA,
                TrajectoryType.LINK,
                TrajectoryType.LOAD,
                TrajectoryType.THERMAL_CAPACITY,
                TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER,
                TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER,
                TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER,
                TrajectoryType.THERMAL_ECONOMIC_PARAMETER,
                TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER,
                TrajectoryType.MISC_CAPACITY,
                TrajectoryType.MISC_LOAD,
                TrajectoryType.RES_CAPACITY,
                TrajectoryType.RES_LOAD,
                TrajectoryType.RES_ZONAL_DISTRIBUTION,
                TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION
        );
        if(supportedTypes.contains(TrajectoryType.valueOf(trajectory.getType()))) {
            checkTrajectoryCoherence(studyId, warningMessageEntities, trajectory, userNni);
        }
        existingLink.ifPresent(studyTrajectoryRepository::delete);

        StudyTrajectoryEntity newLink = StudyTrajectoryEntity.builder()
                .id(StudyTrajectoryKey.builder()
                        .trajectoryId(trajectoryId)
                        .scenarioId(studyId)
                        .build())
                .studyEntity(study)
                .trajectory(trajectory)
                .build();

        StudyTrajectoryEntity saved = studyTrajectoryRepository.save(newLink);
        return saved.getTrajectory();
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

    //********************** Private and utils Methods *****************************//
    //***************************************************************************//
    //***************************************************************************//


    public boolean isStudyTrajectoryExistById(Integer studyId, WarningMessageEntity warning) {
        return warning.getSecondTrajectory() == null || studyTrajectoryRepository.findById(StudyTrajectoryKey.builder()
                        .trajectoryId(warning.getSecondTrajectory().getId())
                        .scenarioId(studyId)
                        .build())
                .isPresent();
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
        Path trajectoryPath = buildTrajectoryPath(trajectoryToUse, TrajectoryType.LOAD);


        // Try to find an existing trajectory
        Optional<TrajectoryEntity> existingTrajectoryOpt = trajectoryRepository
                .findFirstByFileNameAndHorizonAndAreaOrderByVersionDesc(trajectoryToUse, horizon, area);

        if (existingTrajectoryOpt.isPresent()) {
            TrajectoryEntity existingTrajectory = existingTrajectoryOpt.get();
            if ((isSameTrajectory(trajectoryPath, existingTrajectory) && !area.equals(OTHER_AREA))
                    || (area.equals(OTHER_AREA)
                    && isSameVersionOfOtherLoadTrajectory(existingTrajectory, studyId, trajectoryPath, horizon)
            )
            ) {
                throw BusinessException.builder()
                        .message("File already processed with same content {0}")
                        .errorMessageArguments(List.of(trajectoryToUse))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();

            }

            // Update a version and save a new trajectory
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

    // Utility method to build a trajectory path with checks
    public Path buildTrajectoryPath(String trajectoryToUse, TrajectoryType type) throws IOException {
        String nasDir = antaresDataManagerProperties.getNasDirectory();
        String trajFilePath = antaresDataManagerProperties.getTrajectoryFilePath();
        String directoryByType = "";
        if (TrajectoryType.LOAD.equals(type)) {
            directoryByType = antaresDataManagerProperties.getLoadDirectory();
        } else if (TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER.equals(type)) {
            directoryByType = antaresDataManagerProperties.getThermalModulationParameterDirectory();
        } else if (TrajectoryType.MISC_LOAD.equals(type)) {
            directoryByType = antaresDataManagerProperties.getMiscLoadDirectory();
        }

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

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        //download the file
        Path trajectoryFilePath = baseDirectory.resolve(trajectoryToUse).normalize();
        if (!trajectoryFilePath.startsWith(baseDirectory)) {
            throw new IOException("Path is outside of the target directory");
        }

        return trajectoryFilePath;
    }


    private TrajectoryEntity buildNewLoadTrajectory(String trajectoryToUse, String horizon, Path trajectoryPath, String userNni) throws IOException {
        //build a new trajectory
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
        loadTrajectory.setArea(area);
        loadTrajectory.setWarningMessages(warningMessageEntities);
        return trajectoryRepository.save(loadTrajectory);
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
        Path baseDirectory = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(getDirectoryByTrajectoryType(trajectoryType, area, null))
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


    private FsTrajectoryDTO getFsTrajectoryDTO(TrajectoryType trajectoryType, Path path) {
        if (trajectoryType == THERMAL_TECHNICAL_MODULATION_PARAMETER) {
            try {
                return FsTrajectoryDTO.builder()
                        .fileName(path.getFileName().toString())
                        .type(trajectoryType.name())
                        .lastModifiedDate(Files.getLastModifiedTime(path)
                                .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                        .build();
            } catch (IOException e) {
                throw TechnicalException.builder()
                        .message("Can't read trajectory file")
                        .cause(e)
                        .build();
            }
        } else {
            return createFsTrajectoryDTO(path, trajectoryType);
        }
    }


    public Path normalizeAndValidateDirectory(TrajectoryType trajectoryType, String area, String technology) throws IOException {
        String basePath = antaresDataManagerProperties.getNasDirectory();
        String subPath = antaresDataManagerProperties.getTrajectoryFilePath();
        Path baseDirectory = Path.of(basePath).resolve(subPath)
                .resolve(getDirectoryByTrajectoryType(trajectoryType, area, technology))
                .normalize();

        if (!baseDirectory.endsWith("/")) {
            baseDirectory = baseDirectory.resolve("");
        }

        return baseDirectory.normalize();
    }

    private boolean isRelevantFile(Path path, TrajectoryType trajectoryType) {
        return Files.isRegularFile(path) && isValidTrajectoryFile(path, trajectoryType);
    }

    private boolean isDirectoryTrajectory(Path path, TrajectoryType trajectoryType, String area) {
        return Files.isDirectory(path) &&
                (trajectoryType == TrajectoryType.LOAD
                        || trajectoryType == RES_CAPACITY && isDefaultArea(area)
                        || trajectoryType == THERMAL_TECHNICAL_MODULATION_PARAMETER
                        || trajectoryType == TrajectoryType.MISC_LOAD
                        || trajectoryType == TrajectoryType.RES_LOAD);
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


    public String getDirectoryByTrajectoryType(TrajectoryType trajectoryType, String area, String technology) throws IOException {
        return switch (trajectoryType) {
            case AREA -> antaresDataManagerProperties.getAreaDirectory();
            case LINK -> antaresDataManagerProperties.getLinkDirectory();
            case LOAD -> antaresDataManagerProperties.getLoadDirectory();
            case THERMAL_CAPACITY -> getThermalCapacityDirectory(area);
            case THERMAL_TECHNICAL_SPECIFIC_PARAMETER,
                 THERMAL_TECHNICAL_COMMON_PARAMETER -> antaresDataManagerProperties.getThermalParameterDirectory();
            case THERMAL_ECONOMIC_COST_PARAMETER -> antaresDataManagerProperties.getThermalCostDirectory();
            case THERMAL_ECONOMIC_PARAMETER -> antaresDataManagerProperties.getThermalEconomicDirectory();
            case THERMAL_TECHNICAL_MODULATION_PARAMETER -> antaresDataManagerProperties.getThermalModulationParameterDirectory();
            case DSR -> antaresDataManagerProperties.getDsrDirectory();
            case DSR_CAPACITY_MODULATION -> antaresDataManagerProperties.getDsrCapacityDirectory();
            case STS ->
                findChildDirectoryIgnoreCase(Path.of(antaresDataManagerProperties.getNasDirectory())
                        .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                        .resolve(antaresDataManagerProperties.getStsDirectory()), technology).resolve("clusters").toString();
            case MISC_CAPACITY -> antaresDataManagerProperties.getMiscCapacityDirectory();
            case MISC_LOAD -> antaresDataManagerProperties.getMiscLoadDirectory();
            case RES_CAPACITY -> getResCapacityDirectory(area);
            case RES_LOAD -> antaresDataManagerProperties.getResLoadDirectory();
            case RES_ZONAL_DISTRIBUTION, RES_TECHNOLOGY_DISTRIBUTION -> antaresDataManagerProperties.getResDistributionDirectory();
            default -> throw TechnicalException.builder().message("Invalid TrajectoryType: " + trajectoryType).build();
        };
    }

    private String getResCapacityDirectory(String area) {
        return isDefaultArea(area) ?
                Path.of(antaresDataManagerProperties.getResCapacityDirectory()).resolve(area).toString()
                : Path.of(antaresDataManagerProperties.getResCapacityDirectory()).toString();
    }

    private boolean isDefaultArea(String area) {
        return defaultConfigService.fetchAllDefaults()
                .stream().anyMatch(defaultLoadDTO -> defaultLoadDTO.getName().equals(area));
    }

    private String getThermalCapacityDirectory(String area) {
        return isDefaultArea(area) ?
                Path.of(antaresDataManagerProperties.getThermalCapacityDirectory()).resolve(area).toString()
                :
                Path.of(antaresDataManagerProperties.getThermalCapacityDirectory()).toString();
    }


    public void checkTrajectoryCoherence(Integer studyId, Set<WarningMessageEntity> warningMessages, TrajectoryEntity trajectory, String userNni) throws IOException {
        String type = trajectory.getType();

        switch (type) {
            case "LINK" -> checkLinkCoherence(studyId, warningMessages, trajectory, userNni);
            case "AREA" -> checkAreaCoherence(studyId, warningMessages, trajectory, userNni);
            case "LOAD" -> warningMessages = verifyLoad(studyId, warningMessages, trajectory, userNni);
            case "THERMAL_CAPACITY" -> verifyThermalCapacity(studyId, trajectory);
            case "THERMAL_TECHNICAL_COMMON_PARAMETER" -> verifyThermalCommonParameter(studyId, trajectory);
            case "THERMAL_TECHNICAL_SPECIFIC_PARAMETER" -> verifyThermalSpecificParameter(studyId, trajectory);
            case "THERMAL_ECONOMIC_COST_PARAMETER" -> verifyThermalEconomicCostParameter(studyId, trajectory);
            case "THERMAL_ECONOMIC_PARAMETER" -> verifyThermalEconomicParameter(studyId, trajectory);
            case "THERMAL_TECHNICAL_MODULATION_PARAMETER" -> verifyParamModulation(studyId, trajectory);
            case "MISC_CAPACITY"   -> controlesMiscOnSelectInstalledPowerTrajectory(studyId, trajectory);
            case "MISC_LOAD"   -> controlesMiscOnSelectLoadFactorTrajectory(studyId, trajectory);
            case "RES_CAPACITY", "RES_LOAD", "RES_ZONAL_DISTRIBUTION", "RES_TECHNOLOGY_DISTRIBUTION" ->
                    log.info("No additional coherence check for RES trajectory type {} yet", type);
            default -> throw TechnicalException.builder()
                    .message("Trajectory type {0} is not supported")
                    .errorMessageArguments(List.of(type))
                    .build();
        }

        warningMessages.forEach(warning -> warning.setTrajectory(trajectory));
        warningRepository.saveAll(warningMessages);
    }

    /**
     * Validates load factor trajectory on selection.
     * Checks that for each installed power trajectory (specific area or OTHERS),
     * there are corresponding load factor trajectories.
     */
    private void controlesMiscOnSelectLoadFactorTrajectory(Integer studyId, TrajectoryEntity trajectory) throws IOException {
        // Get all installed power trajectories for this study
        List<TrajectoryEntity> installedPowerTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_CAPACITY.name(), studyId);
        
        if (installedPowerTrajectories.isEmpty()) {
            return;
        }

        // Add the trajectory currently being selected
        List<TrajectoryEntity> allLoadFactorTrajectories = new ArrayList<>(
                trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId)
        );
        allLoadFactorTrajectories.add(trajectory);

        // Validate each installed power trajectory against available load factors
        for (TrajectoryEntity installedPowerTraj : installedPowerTrajectories) {
            validateInstalledPowerAgainstLoadFactors(studyId, installedPowerTraj, allLoadFactorTrajectories, trajectory.getHorizon());
        }
    }

    /**
     * Validates installed power trajectory on selection.
     * Checks that for each group/cluster in the installed power trajectory,
     * there are corresponding load factor trajectories.
     */
    private void controlesMiscOnSelectInstalledPowerTrajectory(Integer studyId, TrajectoryEntity trajectory) throws IOException {
        List<GroupAreaMiscCapacity> additionalCapacities = miscClusterCapacityRepository.findByTrajectoryId(trajectory.getId());
        controlesMiscInstalledPower(studyId, additionalCapacities, trajectory.getArea(), trajectory.getHorizon());
    }

    /**
     * Validates installed power on import.
     * Checks that for each group/cluster in the installed power trajectory,
     * there are corresponding load factor trajectories.
     */
    public void controlesMiscOnImportInstalledPower(Integer studyId, List<MiscClusterCapacityEntity> miscClusterCapacityEntities, String area) throws IOException {
        Set<GroupAreaMiscCapacity> additionalCapacities = mapToGroupAreaMiscCapacity(miscClusterCapacityEntities);
        controlesMiscInstalledPower(studyId, new ArrayList<>(additionalCapacities), area, null);
    }

    /**
     * Validates load factor on import.
     * Checks that for each group/cluster in the load factor trajectory,
     * there are corresponding installed power trajectories.
     */
    public void controlesMiscOnImportLoadFactor(Integer studyId, String area, String horizon) throws IOException {
        // Get all installed power trajectories for this study
        List<TrajectoryEntity> installedPowerTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_CAPACITY.name(), studyId);
        
        if (installedPowerTrajectories.isEmpty()) {
            return; // No installed power trajectories yet, import is free
        }

        // Get all load factor trajectories (we're adding a new one during import)
        List<TrajectoryEntity> allLoadFactorTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId);

        // Validate each installed power trajectory against available load factors (including the one being imported)
        for (TrajectoryEntity installedPowerTraj : installedPowerTrajectories) {
            validateInstalledPowerAgainstLoadFactors(studyId, installedPowerTraj, allLoadFactorTrajectories, horizon);
        }
    }

    private Set<WarningMessageEntity> verifyLoad(Integer studyId, Set<WarningMessageEntity> warningMessages, TrajectoryEntity trajectory, String userNni) throws IOException {
        if (OTHER_AREA.equals(trajectory.getArea())) {
            warningMessages = loadFileProcessorService.checkForMissingLoadByAreaFromDb(
                    trajectory.getHorizon(), studyId, userNni, trajectory);
        }
        return warningMessages;
    }

    private void verifyThermalEconomicParameter(Integer studyId, TrajectoryEntity trajectory) throws IOException {
        var listTechno = trajectory.getThermalEconomicCo2s().stream().map(ThermalEconomicCo2Entity::getFuel).map(String::toLowerCase).collect(Collectors.toSet());
        thermalControlService.verifyThermalFuel(studyId, trajectory.getHorizon(), trajectory.getFileName(), listTechno, TrajectoryType.THERMAL_ECONOMIC_PARAMETER);
    }

    public void verifyThermalEconomicCostParameter(Integer studyId, TrajectoryEntity trajectory) throws IOException {
        var listTechno = trajectory.getThermalCosts().stream().map(cost -> cost.getThermalType().getFuel()).map(String::toLowerCase).collect(Collectors.toSet());
        thermalControlService.verifyThermalFuel(studyId, trajectory.getHorizon(), trajectory.getFileName(), listTechno, TrajectoryType.THERMAL_ECONOMIC_COST_PARAMETER);
    }

    private void verifyThermalCommonParameter(Integer studyId, TrajectoryEntity trajectory) {
        Set<String> commonClusters = trajectory.getThermalCommonParameters().stream()
                .map(param -> param.getThermalClusterRef().getName())
                .collect(Collectors.toSet());
        thermalControlService.checkMissingClusters(
                studyId, trajectory.getHorizon(), commonClusters, TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null);
    }

    private void verifyThermalSpecificParameter(Integer studyId, TrajectoryEntity trajectory) {
        Set<String> specificClusters = trajectory.getThermalSpecificParameters().stream()
                .map(param -> param.getThermalClusterRef().getName() + "/" +
                        Optional.ofNullable(param.getArea()).orElse(""))
                .collect(Collectors.toSet());
        thermalControlService.checkMissingClusters(
                studyId, trajectory.getHorizon(), specificClusters, TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER, trajectory.getArea());
    }

    private void verifyThermalCapacity(Integer studyId, TrajectoryEntity trajectory) {
        thermalControlService.verifyClustersInCommonParamTrajectory(studyId, trajectory.getHorizon(), trajectory.getThermalClusterCapacities());
        thermalControlService.verifyClustersInSpecificParamTrajectory(studyId, trajectory.getHorizon(), trajectory.getThermalClusterCapacities());

    }

    public void verifyParamModulation(Integer studyId, TrajectoryEntity trajectory) throws IOException {
        var pathToParamModulation = buildTrajectoryPath(trajectory.getFileName(), TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER);
        trajectory.getThermalModulationParameters().stream()
                .map(ThermalModulationParameterEntity::getTsName)
                .forEach(paramModulationFileName -> {
                    try {
                        thermalParamModulationService.verifyExistingSpecificClustersOfParamModulation(trajectory.getHorizon(), studyId, pathToParamModulation.resolve(Path.of(paramModulationFileName)), trajectory.getFileName(), paramModulationFileName.startsWith("CM") ? "CM" : "MR");
                    } catch (IOException e) {
                        throw TechnicalException.builder().message("could not verify param modulation trajectory on selection" + e.getMessage()).cause(e).build();

                    }
                });
    }

    public void checkLinkCoherence(Integer studyId, Set<WarningMessageEntity> warningMessageEntities, TrajectoryEntity trajectory, String userNni) {
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

    /**
     * Main validation logic for installed power trajectories.
     * For each group/cluster in installed power:
     * - If area != OTHERS: Look for load factor with same area, fallback to OTHERS
     * - If area = OTHERS: Look for load factor with specific areas and OTHERS
     * If no load factor found, import/selection is free (no exception thrown)
     */
    public void controlesMiscInstalledPower(Integer studyId, List<GroupAreaMiscCapacity> additionalCapacities, String area, String horizon) throws IOException {
        List<TrajectoryEntity> loadFactorTrajectories = trajectoryRepository.findByTypeAndStudyId(TrajectoryType.MISC_LOAD.name(), studyId);

        if (loadFactorTrajectories.isEmpty()) {
            return; // No load factor trajectories yet, selection is free
        }

        List<GroupAreaMiscCapacity> capacities = new ArrayList<>(miscClusterCapacityRepository.findByStudyIdAndArea(studyId, area));
        capacities.addAll(additionalCapacities);

        if (capacities.isEmpty()) {
            return; // No capacities to validate
        }

        // Build the installed power map: GroupClusterKey -> Set<Area>
        Map<MiscFileProcessorServiceImpl.GroupClusterKey, Set<String>> installedPowerMap = buildCapacityAreasMap(capacities);

        // Validate each group/cluster in installed power against load factor trajectories
        for (Map.Entry<MiscFileProcessorServiceImpl.GroupClusterKey, Set<String>> entry : installedPowerMap.entrySet()) {
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = entry.getKey();
            Set<String> areasInInstalledPower = entry.getValue();

            validateGroupClusterAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, area, loadFactorTrajectories, horizon);
        }
    }

    /**
     * Validates that for a specific group/cluster in installed power,
     * corresponding load factors exist.
     */
    private void validateGroupClusterAreasAgainstLoadFactors(
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster, Set<String> areasInInstalledPower, String installedPowerArea,
            List<TrajectoryEntity> loadFactorTrajectories, String horizon)
            throws IOException {

        if (OTHER_AREA.equalsIgnoreCase(installedPowerArea)) {
            // For OTHERS installed power: check specific areas + OTHERS in load factor
            validateOthersInstalledPowerAreasAgainstLoadFactors(groupCluster, areasInInstalledPower, loadFactorTrajectories, horizon);
        } else {
            // For specific area installed power: check same area + fallback to OTHERS
            validateSpecificAreaInstalledPowerAgainstLoadFactors(groupCluster,installedPowerArea, loadFactorTrajectories, horizon);
        }
    }

    /**
     * Validates specific area installed power against load factors.
     * For each area in installed power:
     * 1. Try to find load factor with same area
     * 2. If not found, try to find load factor with OTHERS
     * 3. If neither exists, import/selection is free
     */
    private void validateSpecificAreaInstalledPowerAgainstLoadFactors(
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster,
            String installedPowerArea,
            List<TrajectoryEntity> loadFactorTrajectories,
            String horizon) throws IOException {

            // Try to find load factor with the same area
            TrajectoryEntity loadFactorWithArea = findLoadFactorForAreaAndGroup(
                    loadFactorTrajectories, installedPowerArea, horizon
            );

            if (loadFactorWithArea != null) {
                // Found load factor with same area - verify it
                verifyLoadFactorAreaHeaders(loadFactorWithArea, installedPowerArea, groupCluster, horizon);
            } else {
                // No load factor with same area, try OTHERS
                TrajectoryEntity loadFactorOthers = findLoadFactorForAreaAndGroup(
                        loadFactorTrajectories, OTHER_AREA, horizon
                );

                if (loadFactorOthers != null) {
                    // Found OTHERS load factor - verify it
                    verifyLoadFactorAreaHeaders(loadFactorOthers, installedPowerArea, groupCluster, horizon);
                }
                // If neither exists, import/selection is free

        }
    }

    /**
     * Validates OTHERS installed power against load factors.
     * For each area in installed power:
     * Check if there's a load factor with that specific area OR the OTHERS load factor
     */
    public void validateOthersInstalledPowerAreasAgainstLoadFactors(
            MiscFileProcessorServiceImpl.GroupClusterKey groupCluster, Set<String> areasInInstalledPower, List<TrajectoryEntity> loadFactorTrajectories, String horizon) throws IOException {

        for (String area : areasInInstalledPower) {
            // Try to find load factor with the specific area
            TrajectoryEntity loadFactorWithArea = findLoadFactorForAreaAndGroup(loadFactorTrajectories, area, horizon);

            //check specific load factor trajectory
            if (loadFactorWithArea != null) {
                verifyLoadFactorAreaHeaders(loadFactorWithArea, area, groupCluster, horizon);
            } else {
                // Try the OTHERS load factor
                TrajectoryEntity loadFactorOthers = findLoadFactorForAreaAndGroup(
                        loadFactorTrajectories, OTHER_AREA, horizon
                );

                if (loadFactorOthers != null) {
                    verifyLoadFactorAreaHeaders(loadFactorOthers, area, groupCluster, horizon);
                }
                // If neither exists, import/selection is free
            }
        }
    }

    /**
     * Finds a load factor trajectory for a specific area and group/cluster.
     */
    private TrajectoryEntity findLoadFactorForAreaAndGroup(
            List<TrajectoryEntity> loadFactorTrajectories,
            String area,
            String horizon) {

        return loadFactorTrajectories.stream()
                 .filter(t -> t.getArea().equalsIgnoreCase(area))
                 .filter(t -> t.getHorizon().equals(horizon) || horizon == null)
                 .findFirst()
                 .orElse(null);
     }

     /**
      * Verifies that a load factor trajectory contains the expected area header.
      */
     public void verifyLoadFactorAreaHeaders(TrajectoryEntity loadFactorTrajectory, String expectedArea, MiscFileProcessorServiceImpl.GroupClusterKey groupCluster, String horizon)
             throws IOException {

         if (horizon == null) {
             horizon = loadFactorTrajectory.getHorizon();
         }

         Path loadFactorPath = buildTrajectoryPath(loadFactorTrajectory.getFileName(), TrajectoryType.MISC_LOAD);
         List<String> headers = MiscFileProcessorServiceImpl.readHeaderAreas(horizon, loadFactorPath, groupCluster);

         if (!headers.contains(expectedArea.toLowerCase())) {
             throw BusinessException.builder()
                     .message("Load factor trajectory {0} for area {1} does not contain the expected area {2} for group {3}")
                     .errorMessageArguments(List.of(
                             loadFactorTrajectory.getFileName(),
                             loadFactorTrajectory.getArea(),
                             expectedArea,
                             groupCluster.groupe()
                     ))
                     .httpStatus(HttpStatus.BAD_REQUEST)
                     .build();
         }
     }

     /**
      * Validates installed power trajectory for the given installed power trajectory.
      */
     public void validateInstalledPowerAgainstLoadFactors(
             Integer studyId,
             TrajectoryEntity installedPowerTraj,
             List<TrajectoryEntity> allLoadFactorTrajectories,
             String horizon) throws IOException {

         List<GroupAreaMiscCapacity> capacities = miscClusterCapacityRepository.findByTrajectoryId(installedPowerTraj.getId());

         if (capacities.isEmpty()) {
             return;
         }

         Map<MiscFileProcessorServiceImpl.GroupClusterKey, Set<String>> installedPowerMap = buildCapacityAreasMap(capacities);

         for (Map.Entry<MiscFileProcessorServiceImpl.GroupClusterKey, Set<String>> entry : installedPowerMap.entrySet()) {
             MiscFileProcessorServiceImpl.GroupClusterKey groupCluster = entry.getKey();
             Set<String> areasInInstalledPower = entry.getValue();

             validateGroupClusterAreasAgainstLoadFactors(
                     groupCluster, areasInInstalledPower, installedPowerTraj.getArea(),
                     allLoadFactorTrajectories, horizon
             );
         }
     }

     private static Set<GroupAreaMiscCapacity> mapToGroupAreaMiscCapacity(List<MiscClusterCapacityEntity> miscClusterCapacityEntities) {
         return miscClusterCapacityEntities.stream().map(capacity ->

                 new GroupAreaMiscCapacity() {
                     public String getGroupe() {
                         return capacity.getGroupe();
                     }

                     public String getArea() {
                         return capacity.getArea();
                     }

                     public String getCluster() {
                         return capacity.getCluster();
                     }
                 }).collect(Collectors.toSet());
     }

     private Map<MiscFileProcessorServiceImpl.GroupClusterKey, Set<String>> buildCapacityAreasMap(List<GroupAreaMiscCapacity> capacities) {

         return capacities.stream()
                 .collect(Collectors.groupingBy(
                         e -> new MiscFileProcessorServiceImpl.GroupClusterKey(e.getGroupe(), e.getCluster()),
                         Collectors.mapping(e -> e.getArea().toLowerCase(), Collectors.toSet())
                 ));
     }
}
