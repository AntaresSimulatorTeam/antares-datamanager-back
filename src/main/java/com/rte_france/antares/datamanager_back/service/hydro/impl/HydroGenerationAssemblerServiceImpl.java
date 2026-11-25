package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.util.AreaNaming;
import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaKey;
import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import com.rte_france.antares.datamanager_back.dto.HydroAreaGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.mapper.HydroMapper;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroTypeHelper;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.rte_france.antares.datamanager_back.service.hydro.impl.HydroFileProcessorServiceImpl.*;


@Slf4j
@Service
public class HydroGenerationAssemblerServiceImpl implements HydroGenerationAssemblerService {
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final NasFileService nasFileService;
    private final TimeSeriesReader timeSeriesReader;

    private static final String PSP_GENERATING_SUFFIX = "_generating";
    private static final String PSP_PUMPING_SUFFIX = "_pumping";
    private static final String W_HYDRO_OPEN_PREFIX = "w_hydro_open_";

    private static final AreaKey OTHERS_KEY = AreaKey.of(AreaNaming.OTHERS);

    private record TrajectoryFileContext(Path path, TrajectoryType type) {}

    public HydroGenerationAssemblerServiceImpl(
            NasFileService nasFileService,
            AntaresDataManagerProperties antaresDataManagerProperties,
            TimeSeriesReader timeSeriesReader) {
        this.nasFileService = nasFileService;
        this.antaresDataManagerProperties = antaresDataManagerProperties;
        this.timeSeriesReader = timeSeriesReader;
    }

    @ExecutionTime("HYDRO")
    @Override
    public Map<AreaKey, HydroAreaGenerationDTO> assembleHydroProperties(StudyEntity studyEntity) throws BusinessException {
        Map<AreaKey, HydroGenerationDTO> hydroDtoByArea = new HashMap<>();
        Map<AreaKey, HydroGenerationDTO> pspDtoByArea = new HashMap<>();

        assembleParametersAndAllocation(getTechTrajectories(studyEntity, false), hydroDtoByArea);
        assembleParametersAndAllocation(getTechTrajectories(studyEntity, true), pspDtoByArea);

        setSeriesOnDtos(createArrowSeriesForType(studyEntity, false), hydroDtoByArea);
        setSeriesOnDtos(createArrowSeriesForType(studyEntity, true), pspDtoByArea);

        Set<AreaKey> allAreas = new HashSet<>(hydroDtoByArea.keySet());
        allAreas.addAll(pspDtoByArea.keySet());

        Map<AreaKey, HydroAreaGenerationDTO> result = new HashMap<>();
        allAreas.forEach(area -> result.put(area, new HydroAreaGenerationDTO(hydroDtoByArea.get(area), pspDtoByArea.get(area))));
        return result;
    }

    private List<TrajectoryEntity> getTechTrajectories(StudyEntity studyEntity, boolean isPsp) {
        String type = isPsp
                ? TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name()
                : TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name();
        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> type.equals(t.getType()))
                .toList();
    }

    private void assembleParametersAndAllocation(List<TrajectoryEntity> techTrajectories, Map<AreaKey, HydroGenerationDTO> dtoByArea) {
        if (techTrajectories.isEmpty()) return;

        Set<AreaKey> areasWithSpecificParams = getAreasWithSpecificHydroProperties(techTrajectories);

        techTrajectories.stream()
                .flatMap(t -> filterHydroParametersEntities(t, areasWithSpecificParams))
                // A blank node can't be a study area: skip it before building the key
                .filter(hp -> hp.getNode() != null && !hp.getNode().isBlank())
                .forEach(hp -> dtoByArea.computeIfAbsent(AreaKey.of(hp.getNode()), k -> HydroMapper.mapToHydroGenerationDTO(hp)));

        techTrajectories.stream()
                .flatMap(t -> filterHydroAllocationEntities(t, areasWithSpecificParams))
                .filter(ha -> ha.getHydro() != null && ha.getLoad() != null)
                .filter(ha -> !extractAllocationArea(ha.getHydro()).isBlank())
                .forEach(ha -> {
                    AreaKey area = AreaKey.of(extractAllocationArea(ha.getHydro()));
                    String load = ha.getLoad().toUpperCase(Locale.ROOT);
                    Double allocation = ha.getAllocation() != null ? ha.getAllocation().doubleValue() : 0.0;
                    HydroGenerationDTO dto = dtoByArea.get(area);
                    if (dto != null) {
                        if (dto.getAllocation() == null) dto.setAllocation(new HashMap<>());
                        dto.getAllocation().put(load, allocation);
                    }
                });
    }

    private void setSeriesOnDtos(Map<AreaKey, List<String>> seriesByArea, Map<AreaKey, HydroGenerationDTO> dtoByArea) {
        seriesByArea.forEach((area, series) ->
                dtoByArea.computeIfAbsent(area, k -> HydroMapper.mapToHydroGenerationDTO(null))
                         .setSeries(series.toArray(String[]::new)));
    }

    private Set<AreaKey> getAreasWithSpecificHydroProperties(List<TrajectoryEntity> hydroTechnicalTrajectories) {
        return hydroTechnicalTrajectories.stream()
                .filter(t -> t.getArea() != null && !t.getArea().isBlank() && !AreaNaming.isOthers(t.getArea()))
                .map(t -> AreaKey.of(t.getArea()))
                .collect(Collectors.toSet());
    }

    private Stream<HydroParametersEntity> filterHydroParametersEntities(
            TrajectoryEntity trajectory,
            Set<AreaKey> areasWithSpecificParameters
    ) {
        return filterEntities(
                trajectory.getHydroParametersEntities(),
                trajectory,
                areasWithSpecificParameters,
                HydroParametersEntity::getNode
        );
    }

    private Stream<HydroAllocationEntity> filterHydroAllocationEntities(
            TrajectoryEntity trajectory,
            Set<AreaKey> areasWithSpecificParameters
    ) {
        return filterEntities(
                trajectory.getHydroAllocationEntities(),
                trajectory,
                areasWithSpecificParameters,
                ha -> extractAllocationArea(ha.getHydro())
        );
    }

    private <T> Stream<T> filterEntities(
            List<T> entities,
            TrajectoryEntity trajectory,
            Set<AreaKey> areasWithSpecificParameters,
            Function<T, String> areaExtractor
    ) {
        if (entities == null || entities.isEmpty()) {
            return Stream.empty();
        }
        boolean isOthersTrajectory = AreaNaming.isOthers(trajectory.getArea());

        return entities.stream()
                .filter(entity -> shouldIncludeEntity(entity, isOthersTrajectory, areasWithSpecificParameters, areaExtractor));
    }

    private <T> boolean shouldIncludeEntity(
            T entity,
            boolean isOthersTrajectory,
            Set<AreaKey> areasWithSpecificCapacity,
            Function<T, String> areaExtractor
    ) {
        if (!isOthersTrajectory) {
            return true;
        }
        String entityArea = areaExtractor.apply(entity);
        return entityArea == null || entityArea.isBlank() || !areasWithSpecificCapacity.contains(AreaKey.of(entityArea));
    }

    // The area of an allocation row, as written: "w_hydro_open_FR" gives "FR"
    private String extractAllocationArea(String hydroColumnValue) {
        if (hydroColumnValue == null) return "";
        boolean hasPrefix = hydroColumnValue.regionMatches(true, 0, W_HYDRO_OPEN_PREFIX, 0, W_HYDRO_OPEN_PREFIX.length());
        return hasPrefix ? hydroColumnValue.substring(W_HYDRO_OPEN_PREFIX.length()) : hydroColumnValue;
    }

    private Map<AreaKey, List<String>> createArrowSeriesForType(StudyEntity studyEntity, boolean isPsp) throws BusinessException {
        Map<AreaKey, List<TrajectoryFileContext>> seriesPathByArea = mapTsPathByArea(studyEntity, isPsp);
        Map<AreaKey, List<String>> generatedFilesByArea = new HashMap<>();

        Set<AreaKey> processedAreas = new HashSet<>();

        // 1. Specific areas treatment
        for (AreaKey area : nonOtherAreas(seriesPathByArea.keySet(), processedAreas)) {
            List<TrajectoryFileContext> contexts = seriesPathByArea.get(area);
            if (contexts != null && !contexts.isEmpty()) {
                List<String> generatedFilesArrow = new ArrayList<>();
                processSeriesByArea(studyEntity, area, contexts, generatedFilesArrow);
                generatedFilesByArea.put(area, generatedFilesArrow);
            }
        }

        // 2. OTHER case
        List<TrajectoryFileContext> othersTsPath = seriesPathByArea.get(OTHERS_KEY);
        if (processedAreas.contains(OTHERS_KEY) && othersTsPath != null && !othersTsPath.isEmpty()) {
            Set<AreaKey> alreadyProcessedAreas = Set.copyOf(generatedFilesByArea.keySet());

            Set<AreaKey> remainingOtherAreas = othersTsPath.stream()
                    .filter(Objects::nonNull)
                    .filter(context -> context.path().getFileName() != null)
                    .filter(context -> !isMaxpowerFile(context.path().getFileName().toString()))
                    .map(context -> extractAreaFromFileName(context.path()))
                    .flatMap(Optional::stream)
                    .filter(area -> !alreadyProcessedAreas.contains(area))
                    .collect(Collectors.toSet());

            for (AreaKey area : remainingOtherAreas) {
                List<TrajectoryFileContext> pathsForArea = othersTsPath.stream()
                        .filter(context -> belongsToAreaOrIsMaxpower(context.path(), area))
                        .toList();

                if (!pathsForArea.isEmpty()) {
                    List<String> generatedFilesArrow = new ArrayList<>();
                    processSeriesByArea(studyEntity, area, pathsForArea, generatedFilesArrow);
                    generatedFilesByArea.put(area, generatedFilesArrow);
                }
            }
        }

        return generatedFilesByArea;
    }

    private boolean belongsToAreaOrIsMaxpower(Path path, AreaKey area) {
        if (path == null || path.getFileName() == null) {
            return false;
        }

        if (isMaxpowerFile(path.getFileName().toString())) {
            return true;
        }

        return extractAreaFromFileName(path)
                .map(area::equals)
                .orElse(false);
    }

    private boolean isMaxpowerFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).startsWith("maxpower_");
    }

    // The area in a series file name: "mingen_FR_2030.csv" gives FR
    private Optional<AreaKey> extractAreaFromFileName(Path path) {
        if (path == null || path.getFileName() == null) {
            return Optional.empty();
        }

        String fileName = path.getFileName().toString();
        String fileNameWithoutExtension = fileName.replaceFirst("\\.[^.]+$", "");
        String[] parts = fileNameWithoutExtension.split("_");

        if (parts.length < 3) {
            return Optional.empty();
        }

        String area = parts[parts.length - 2];
        return area.isBlank() ? Optional.empty() : Optional.of(AreaKey.of(area));
    }

    private void processSeriesByArea(StudyEntity studyEntity, AreaKey area, List<TrajectoryFileContext> hydroSeriesContexts, List<String> generatedFilesArrow) throws BusinessException {
        boolean skipRorMaxpower = isRorOnlyArea(hydroSeriesContexts);
        String outputDir = antaresDataManagerProperties.getHydroTsOutputDirectory();
        String horizon = studyEntity.getHorizon();

        hydroSeriesContexts.forEach(context -> {
            boolean isPsp = HydroTypeHelper.isPsp(context.type());
            String fileName = context.path().getFileName().toString();
            String pspMarker = isPsp ? "_psp" : "";

            if (fileName.startsWith("maxpower")) {
                if (isPsp && skipRorMaxpower) return;
                generatedFilesArrow.add(generateMaxpowerArrow(context.path(), area, isPsp, pspMarker, outputDir, horizon));
            } else {
                generatedFilesArrow.add(generateSeriesArrow(context.path(), area, isPsp, pspMarker, outputDir, horizon, fileName));
            }
        });
    }

    private String generateMaxpowerArrow(Path path, AreaKey area, boolean isPsp, String pspMarker, String outputDir, String horizon) {
        try {
            Set<String> columnsToRead = isPsp
                    ? Set.of(area.value() + PSP_GENERATING_SUFFIX, area.value() + PSP_PUMPING_SUFFIX)
                    : Collections.singleton(area.value());
            TimeSeriesMatrix matrix = timeSeriesReader.readSelectedColumnsFromXlsx(path, horizon, columnsToRead);
            if (isPsp) {
                validatePspMaxpowerColumns(matrix, area);
            }
            return nasFileService.saveMatrixToNas(matrix, area.value() + pspMarker + "_maxpower", outputDir);
        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Could not generate matrix for maxpower")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validatePspMaxpowerColumns(TimeSeriesMatrix matrix, AreaKey area) {
        String generatingColumn = area.value() + PSP_GENERATING_SUFFIX;
        String pumpingColumn = area.value() + PSP_PUMPING_SUFFIX;
        boolean hasGenerating = matrix.columns().stream().anyMatch(c -> c.name().equalsIgnoreCase(generatingColumn));
        boolean hasPumping    = matrix.columns().stream().anyMatch(c -> c.name().equalsIgnoreCase(pumpingColumn));
        if (!hasGenerating || !hasPumping) {
            throw BusinessException.builder()
                    .errorMessageArguments(List.of(generatingColumn, pumpingColumn, area.value()))
                    .message("PSP maxpower file must contain columns {0} and {1} for area {2}")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private String generateSeriesArrow(Path path, AreaKey area, boolean isPsp, String pspMarker, String outputDir, String horizon, String fileName) {
        try {
            TimeSeriesMatrix matrix = nasFileService.readMatrix(path, horizon, false, "", "");
            return nasFileService.saveMatrixToNas(matrix, area.value() + pspMarker + "_" + getHydroSeriesType(fileName), outputDir);
        } catch (IOException e) {
            throw BusinessException.builder()
                    .message("Could not generate matrix for " + HydroTypeHelper.getSeriesLabel(isPsp))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private Set<AreaKey> nonOtherAreas(Set<AreaKey> areas, Set<AreaKey> listAreas) {
        Set<AreaKey> result = new HashSet<>(areas);
        listAreas.addAll(areas);
        result.remove(OTHERS_KEY);
        return result;
    }

    private Map<AreaKey, List<TrajectoryFileContext>> mapTsPathByArea(StudyEntity study, boolean isPsp) {
        Path trajectoryFilePath = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath());
        String targetType = isPsp ? TrajectoryType.HYDRO_PSP_SERIES.name() : TrajectoryType.HYDRO_SERIES.name();
        Path seriesDir = trajectoryFilePath.resolve(isPsp
                ? antaresDataManagerProperties.getPspSeriesDirectory()
                : antaresDataManagerProperties.getHydroSeriesDirectory());

        return study.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(trajectory -> targetType.equals(trajectory.getType()))
                .filter(trajectory -> trajectory.getArea() != null && !trajectory.getArea().isBlank())
                .flatMap(trajectory -> Optional.ofNullable(trajectory.getHydroSeriesEntities())
                        .orElseGet(Collections::emptyList)
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(hydroSeries -> hydroSeries.getTsName() != null)
                        .filter(hydroSeries -> shouldKeepHydroSeries(study, trajectory, hydroSeries))
                        .map(hydroSeries -> Map.entry(
                                AreaKey.of(trajectory.getArea()),
                                new TrajectoryFileContext(
                                        resolveHydroSeriesPath(seriesDir, trajectory, hydroSeries),
                                        TrajectoryType.valueOf(trajectory.getType())
                                )
                        )))
                .filter(entry -> Files.exists(entry.getValue().path()))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    private boolean shouldKeepHydroSeries(StudyEntity study, TrajectoryEntity trajectory, HydroSeriesEntity hydroSeries) {
        String tsName = hydroSeries.getTsName();

        if (!tsName.toLowerCase(Locale.ROOT).startsWith(HYDRO_SERIES_RESERVOIR_LEVELS)) {
            return true;
        }

        AreaKey area = AreaNaming.isOthers(trajectory.getArea())
                ? extractAreaFromFileName(Path.of(tsName)).orElse(null)
                : AreaKey.of(trajectory.getArea());

        if (area == null) {
            return false;
        }

        String targetTechType = TrajectoryType.HYDRO_PSP_SERIES.name().equals(trajectory.getType())
                ? TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name()
                : TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name();

        return study.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(studyTrajectory -> targetTechType.equals(studyTrajectory.getType()))
                .flatMap(studyTrajectory -> Optional.ofNullable(studyTrajectory.getHydroParametersEntities())
                        .orElseGet(Collections::emptyList)
                        .stream())
                .filter(Objects::nonNull)
                .filter(hydroParameter -> hydroParameter.getNode() != null && !hydroParameter.getNode().isBlank())
                .filter(hydroParameter -> area.equals(AreaKey.of(hydroParameter.getNode())))
                .anyMatch(hydroParameter -> Boolean.TRUE.equals(hydroParameter.getReservoir()));
    }

    private Path resolveHydroSeriesPath(Path hydroSeriesDir, TrajectoryEntity trajectory, HydroSeriesEntity hydroSeries) {
        String tsName = hydroSeries.getTsName();

        Path trajectoryDir = hydroSeriesDir.resolve(trajectory.getFileName());
        
        // hydroParametersEntities

        if (isMaxpowerFile(tsName)) {
            return trajectoryDir.resolve(tsName);
        }

        return resolveHydroSeriesSubDirectory(tsName)
                .map(subDirectory -> trajectoryDir.resolve(subDirectory).resolve(tsName))
                .orElseGet(() -> trajectoryDir.resolve(tsName));
    }

    private boolean isRorOnlyArea(List<TrajectoryFileContext> contexts) {
        return contexts.stream()
                .filter(ctx -> !isMaxpowerFile(ctx.path().getFileName().toString()))
                .allMatch(ctx -> ctx.path().getFileName().toString().toLowerCase(Locale.ROOT).startsWith(HYDRO_SERIES_INFLOWS_ROR + "_"));
    }

    private Optional<String> resolveHydroSeriesSubDirectory(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }

        if (fileName.startsWith(HYDRO_SERIES_INFLOWS_ROR) || fileName.startsWith(HYDRO_SERIES_INFLOWS_MOD)) {
            return Optional.of("inflows");
        }

        if (fileName.startsWith(HYDRO_SERIES_MINGEN)) {
            return Optional.of("mingen");
        }

        if (fileName.startsWith(HYDRO_SERIES_RESERVOIR_LEVELS)) {
            return Optional.of("reservoir_levels");
        }

        return Optional.empty();
    }

    private String getHydroSeriesType(String fileName) {
        if (fileName.startsWith(HYDRO_SERIES_MINGEN)) {
            return HYDRO_SERIES_MINGEN;
        }
        if (fileName.startsWith(HYDRO_SERIES_RESERVOIR_LEVELS)) {
            return HYDRO_SERIES_RESERVOIR_LEVELS;
        }
        if (fileName.startsWith(HYDRO_SERIES_INFLOWS_MOD)) {
            return HYDRO_SERIES_INFLOWS_MOD;
        }
        if (fileName.startsWith(HYDRO_SERIES_INFLOWS_ROR)) {
            return HYDRO_SERIES_INFLOWS_ROR;
        }
        return null;
    }
}
