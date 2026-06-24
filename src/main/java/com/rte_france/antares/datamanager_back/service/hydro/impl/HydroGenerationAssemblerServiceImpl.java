package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
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

import static com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl.OTHER_AREA;
import static com.rte_france.antares.datamanager_back.service.hydro.impl.HydroFileProcessorServiceImpl.*;


@Slf4j
@Service
public class HydroGenerationAssemblerServiceImpl implements HydroGenerationAssemblerService {
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final NasFileService nasFileService;
    private final TimeSeriesReader timeSeriesReader;

    private record TrajectoryFileContext(Path path, TrajectoryType type) {}

    public HydroGenerationAssemblerServiceImpl(
            NasFileService nasFileService,
            AntaresDataManagerProperties antaresDataManagerProperties,
            TimeSeriesReader timeSeriesReader) {
        this.nasFileService = nasFileService;
        this.antaresDataManagerProperties = antaresDataManagerProperties;
        this.timeSeriesReader = timeSeriesReader;
    }
    
    @Override
    public Map<String, List<HydroGenerationDTO>> assembleHydroProperties(StudyEntity studyEntity) throws BusinessException {
        Map<String, List<String>> generatedFilesArrowNameByArea = createArrowSeriesForHydroSeries(studyEntity);
        Map<String, List<HydroGenerationDTO>> hydroGenerationDTO = new HashMap<>();
        Set<String> areasWithSpecificHydroProperties = getAreasWithSpecificHydroProperties(studyEntity, TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name());

        List<TrajectoryEntity> hydroTechnicalTrajectories = studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name().equals(t.getType()) ||
                        TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name().equals(t.getType()))
                .toList();
        
        hydroTechnicalTrajectories.stream()
                        .flatMap(trajectory -> filterHydroParametersEntities(trajectory, areasWithSpecificHydroProperties))
                        .filter(hydroParameter -> hydroParameter.getNode() != null)
                        .forEach(hydroParameter -> {
                            String node = hydroParameter.getNode().toUpperCase(Locale.ROOT);
                            hydroGenerationDTO.computeIfAbsent(node, key -> new ArrayList<>())
                                    .add(HydroMapper.mapToHydroGenerationDTO(hydroParameter));
                        });
        
        hydroTechnicalTrajectories.stream()
                        .flatMap(trajectory -> filterHydroAllocationEntities(trajectory, areasWithSpecificHydroProperties))
                        .filter(hydroAllocation -> hydroAllocation.getHydro() != null && hydroAllocation.getLoad() != null)
                        .forEach(hydroAllocation -> {
                            String hydro = hydroAllocation.getHydro().toUpperCase(Locale.ROOT);
                            String load = hydroAllocation.getLoad().toUpperCase(Locale.ROOT);
                            Double allocation = hydroAllocation.getAllocation() != null
                                    ? hydroAllocation.getAllocation().doubleValue()
                                    : 0.0;

                            List<HydroGenerationDTO> hydroDtos = hydroGenerationDTO.get(hydro);
                            if (hydroDtos != null) {
                                hydroDtos.forEach(dto -> {
                                    if (dto.getAllocation() == null) {
                                        dto.setAllocation(new HashMap<>());
                                    }
                                    dto.getAllocation().put(load, allocation);
                                });
                            }
                        });

        generatedFilesArrowNameByArea.forEach((area, generatedFilesArrowNames) -> {
            String normalizedArea = area.toUpperCase(Locale.ROOT);

            List<HydroGenerationDTO> hydroDtos = hydroGenerationDTO.get(normalizedArea);

            if (hydroDtos != null) {
                hydroDtos.forEach(dto -> dto.setSeries(generatedFilesArrowNames.toArray(String[]::new)));
            }
        });

        return hydroGenerationDTO;
    }

    private Set<String> getAreasWithSpecificHydroProperties(StudyEntity studyEntity, String type) {
        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> type.equals(t.getType()))
                .filter(t -> t.getArea() != null && !OTHER_AREA.equalsIgnoreCase(t.getArea()))
                .map(t -> t.getArea().toUpperCase())
                .collect(Collectors.toSet());
    }

    private Stream<HydroParametersEntity> filterHydroParametersEntities(
            TrajectoryEntity trajectory,
            Set<String> areasWithSpecificParameters
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
            Set<String> areasWithSpecificParameters
    ) {
        return filterEntities(
                trajectory.getHydroAllocationEntities(),
                trajectory,
                areasWithSpecificParameters,
                HydroAllocationEntity::getHydro
        );
    }

    private <T> Stream<T> filterEntities(
            List<T> entities,
            TrajectoryEntity trajectory,
            Set<String> areasWithSpecificParameters,
            Function<T, String> areaExtractor
    ) {
        if (entities == null || entities.isEmpty()) {
            return Stream.empty();
        }
        String trajectoryArea = trajectory.getArea() != null ? trajectory.getArea().toUpperCase() : "";
        boolean isOthersTrajectory = OTHER_AREA.equalsIgnoreCase(trajectoryArea);

        return entities.stream()
                .filter(entity -> shouldIncludeEntity(entity, isOthersTrajectory, areasWithSpecificParameters, areaExtractor));
    }

    private <T> boolean shouldIncludeEntity(
            T entity,
            boolean isOthersTrajectory,
            Set<String> areasWithSpecificCapacity,
            Function<T, String> areaExtractor
    ) {
        if (!isOthersTrajectory) {
            return true;
        }
        String entityArea = Optional.ofNullable(areaExtractor.apply(entity))
                .map(String::toUpperCase)
                .orElse("");

        return !areasWithSpecificCapacity.contains(entityArea);
    }

    private Map<String, List<String>> createArrowSeriesForHydroSeries(StudyEntity studyEntity) throws BusinessException {
        Map<String, List<TrajectoryFileContext>> hydroSeriesPathByArea = mapTsPathByArea(studyEntity);
        Map<String, List<String>> generatedFilesByArea = new HashMap<>();

        Set<String> processedAreas = new HashSet<>();
        String otherArea = OTHER_AREA.toUpperCase(Locale.ROOT);

        // 1. Specific areas treatment
        for (String area : nonOtherAreas(hydroSeriesPathByArea.keySet(), processedAreas)) {
            List<TrajectoryFileContext> hydroSeriesContexts = hydroSeriesPathByArea.get(area);

            if (hydroSeriesContexts != null && !hydroSeriesContexts.isEmpty()) {
                List<String> generatedFilesArrow = new ArrayList<>();
                processSeriesByArea(studyEntity, area, hydroSeriesContexts, generatedFilesArrow);
                generatedFilesByArea.put(area, generatedFilesArrow);
            }
        }

        // 2. OTHER case
        List<TrajectoryFileContext> othersTsPath = hydroSeriesPathByArea.get(otherArea);
        if (processedAreas.contains(otherArea) && othersTsPath != null && !othersTsPath.isEmpty()) {
            Set<String> alreadyProcessedAreas = generatedFilesByArea.keySet().stream()
                    .filter(Objects::nonNull)
                    .map(area -> area.toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            Set<String> remainingOtherAreas = othersTsPath.stream()
                    .filter(Objects::nonNull)
                    .filter(context -> context.path().getFileName() != null)
                    .filter(context -> !isMaxpowerFile(context.path().getFileName().toString()))
                    .map(context -> extractAreaFromFileName(context.path()))
                    .flatMap(Optional::stream)
                    .map(area -> area.toUpperCase(Locale.ROOT))
                    .filter(area -> !alreadyProcessedAreas.contains(area))
                    .collect(Collectors.toSet());

            for (String area : remainingOtherAreas) {
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

    private boolean belongsToAreaOrIsMaxpower(Path path, String area) {
        if (path == null || path.getFileName() == null) {
            return false;
        }

        if (isMaxpowerFile(path.getFileName().toString())) {
            return true;
        }

        return extractAreaFromFileName(path)
                .map(extractedArea -> area.equals(extractedArea.toUpperCase(Locale.ROOT)))
                .orElse(false);
    }

    private boolean isMaxpowerFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).startsWith("maxpower_");
    }

    private Optional<String> extractAreaFromFileName(Path path) {
        if (path == null || path.getFileName() == null) {
            return Optional.empty();
        }

        String fileName = path.getFileName().toString();
        String fileNameWithoutExtension = fileName.replaceFirst("\\.[^.]+$", "");
        String[] parts = fileNameWithoutExtension.split("_");

        if (parts.length < 3) {
            return Optional.empty();
        }

        return Optional.of(parts[parts.length - 2].toUpperCase(Locale.ROOT));
    }

    private void processSeriesByArea(StudyEntity studyEntity, String area, List<TrajectoryFileContext> hydroSeriesContexts, List<String> generatedFilesArrow) throws BusinessException {
        hydroSeriesContexts.forEach(context -> {
            String outputFileName;
            TimeSeriesMatrix matrix;
            Path path = context.path();
            TrajectoryType type = context.type();
            String fileName = path.getFileName().toString();
            boolean isPsp = HydroTypeHelper.isPsp(type);
            String pspMarker = isPsp ? "_psp" : "";
            String outputDir = antaresDataManagerProperties.getHydroTsOutputDirectory();

            if (fileName.startsWith("maxpower")) {
                try {
                    Set<String> columnsToRead = isPsp
                            ? Set.of(area + "_generating", area + "_pumping")
                            : Collections.singleton(area);

                    matrix = timeSeriesReader.readSelectedColumnsFromXlsx(path, studyEntity.getHorizon(), columnsToRead);
                    outputFileName = nasFileService.saveMatrixToNas(matrix, area.toUpperCase() + pspMarker + "_maxpower", outputDir);
                } catch (IOException e) {
                    throw BusinessException.builder()
                            .message("Could not generate matrix for maxpower")
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            } else {
                try {
                    matrix = nasFileService.readMatrix(path, studyEntity.getHorizon(), false);
                    outputFileName = nasFileService.saveMatrixToNas(matrix, area.toUpperCase() + pspMarker + "_" + getHydroSeriesType(fileName), outputDir);
                } catch (IOException e) {
                    throw BusinessException.builder()
                            .message("Could not generate matrix for " + HydroTypeHelper.getSeriesLabel(isPsp))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            }
            generatedFilesArrow.add(outputFileName);
        });
    }

    private Set<String> nonOtherAreas(Set<String> areas, Set<String> listAreas) {
        Set<String> result = new HashSet<>(areas);
        listAreas.addAll(areas);
        result.remove(OTHER_AREA.toUpperCase());
        return result;
    }

    private Map<String, List<TrajectoryFileContext>> mapTsPathByArea(StudyEntity study) {
        Path trajectoryFilePath = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath());
        Path hydroSeriesDir = trajectoryFilePath.resolve(antaresDataManagerProperties.getHydroSeriesDirectory());
        Path pspSeriesDir = trajectoryFilePath.resolve(antaresDataManagerProperties.getPspSeriesDirectory());

        return study.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(trajectory -> TrajectoryType.HYDRO_SERIES.name().equals(trajectory.getType()) ||
                        TrajectoryType.HYDRO_PSP_SERIES.name().equals(trajectory.getType()))
                .filter(trajectory -> trajectory.getArea() != null)
                .flatMap(trajectory -> {
                    Path seriesDir = TrajectoryType.HYDRO_PSP_SERIES.name().equals(trajectory.getType())
                            ? pspSeriesDir
                            : hydroSeriesDir;
                    return Optional.ofNullable(trajectory.getHydroSeriesEntities())
                        .orElseGet(Collections::emptyList)
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(hydroSeries -> hydroSeries.getTsName() != null)
                        .filter(hydroSeries -> shouldKeepHydroSeries(study, trajectory, hydroSeries))
                        .map(hydroSeries -> Map.entry(
                                trajectory.getArea().toUpperCase(Locale.ROOT),
                                new TrajectoryFileContext(
                                        resolveHydroSeriesPath(seriesDir, trajectory, hydroSeries),
                                        TrajectoryType.valueOf(trajectory.getType())
                                )
                        ));
                })
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

        String area = OTHER_AREA.equalsIgnoreCase(trajectory.getArea())
                ? extractAreaFromFileName(Path.of(tsName)).orElse(null)
                : trajectory.getArea();

        if (area == null) {
            return false;
        }

        String normalizedArea = area.toUpperCase(Locale.ROOT);

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
                .filter(hydroParameter -> hydroParameter.getNode() != null)
                .filter(hydroParameter -> normalizedArea.equals(hydroParameter.getNode().toUpperCase(Locale.ROOT)))
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
