package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.StsConstraintParameterDTO;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.mapper.StStorageMapper;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.sts.StsGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.service.sts.StsTsFile;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StsPropertiesAssemblerServiceImpl implements StsGenerationAssemblerService {

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final NasFileService nasFileService;
    private final TimeSeriesReader timeSeriesReader;



    @Override
    public Map<String, StsGenerationDTO> assembleStsProperties(StudyEntity studyEntity) {
        List<StorageConstraintsContext> contexts = buildStorageConstraintsContext(studyEntity);

        Set<String> allAreas = studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.STS.name().equals(t.getType()))
                .flatMap(t -> t.getStStorageEntities().stream()
                        .filter(Objects::nonNull)
                        .filter(s -> Boolean.TRUE.equals(s.getConstraintsFlag()))
                        .map(StStorageEntity::getArea)
                        .filter(Objects::nonNull)
                        .map(String::toUpperCase)
                )
                .collect(Collectors.toSet());

        Map<String, List<String>> constraintsByArea = createConstraintsTsFiles(contexts, allAreas, studyEntity.getHorizon());
        String horizon = studyEntity.getHorizon();



        List<StStorageEntity> eligibleEntities = studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.STS.name().equals(t.getType()))
                .map(TrajectoryEntity::getStStorageEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(sts -> {
                    double injection = sts.getInjection() != null ? sts.getInjection().doubleValue() : 0.0;
                    double withdrawal = sts.getWithdrawal() != null ? sts.getWithdrawal().doubleValue() : 0.0;
                    double storage = sts.getStorage() != null ? sts.getStorage().doubleValue() : 0.0;
                    return (injection + withdrawal + storage) > 0;
                })
                .toList();


        // Pre-compute constraint parameters on the main thread while the Hibernate session is active,
        Map<Integer, Map<String, StsConstraintParameterDTO>> constraintParamsById =
                contexts.stream()
                        .collect(Collectors.toMap(
                                ctx -> ctx.storage().getId(),
                                ctx -> mapConstraintParametersFromContext(ctx, allAreas),
                                (map1, map2) -> {
                                    map1.putAll(map2);
                                    return map1;
                                }
                        ));

        ConcurrentHashMap<Path, TimeSeriesMatrix> matrixCache = new ConcurrentHashMap<>();
        ConcurrentHashMap<Path, byte[]> bytesCache = new ConcurrentHashMap<>();

        return eligibleEntities.stream()
                .collect(Collectors.toConcurrentMap(
                        sts -> sts.getArea().toUpperCase() + "_" + sts.getName(),
                        sts -> {
                            StsGenerationDTO dto = StStorageMapper.mapToStsGenerationDTO(sts);
                            dto.setStsTsList(this.createMatrixStsTsFiles(sts, horizon, matrixCache, bytesCache));
                            String clusterKey = sts.getArea().toUpperCase(Locale.ROOT) + "_" + sts.getName();
                            dto.setStsConstraintsSeriesList(
                                    constraintsByArea.getOrDefault(clusterKey, List.of()));
                            dto.setConstraintParameters(constraintParamsById.get(sts.getId()));
                            return dto;
                        },
                        (existing, replacement) -> existing
                ));
    }


    private Map<String, List<String>> createConstraintsTsFiles(
            List<StorageConstraintsContext> contexts,
            Set<String> allAreas,
            String horizon
    ) {
        Map<String, Map<String, LinkedHashSet<String>>> uniqueByAreaAndCluster = new ConcurrentHashMap<>();
        allAreas.forEach(area -> uniqueByAreaAndCluster.put(area, new ConcurrentHashMap<>()));
        Map<ConstraintSeriesKey, String> savedSeriesByKey = new ConcurrentHashMap<>();

        // Group contexts by file path to read each xlsx only once
        Map<Path, List<StorageConstraintsContext>> contextsByFile = contexts.stream()
                .collect(Collectors.groupingBy(StorageConstraintsContext::file));

        String outputDir = antaresDataManagerProperties.getStsTsOutputDirectory();

        // Each file entry is independent and can be processed separately.
        contextsByFile.forEach((file, value) -> {
            Set<String> requiredColumnsLower = value.stream()
                    .flatMap(ctx -> ctx.parameterNames().stream())
                    .filter(Objects::nonNull)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            if (requiredColumnsLower.isEmpty()) {
                return;
            }

            TimeSeriesMatrix matrix;
            try {
                matrix = timeSeriesReader.readSelectedColumnsFromXlsx(file, horizon, requiredColumnsLower);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            Map<String, Map<String, TimeSeriesMatrix>> columnsIndexByArea = indexMatrixColumnsByArea(matrix, allAreas);

            for (StorageConstraintsContext ctx : value) {
                writeConstraintsForContext(ctx, file, allAreas, columnsIndexByArea, outputDir, uniqueByAreaAndCluster, savedSeriesByKey);
            }
        });

        return uniqueByAreaAndCluster.values().stream()
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new ArrayList<>(entry.getValue())));
    }

    private void writeConstraintsForContext(
            StorageConstraintsContext ctx,
            Path constraintsFile,
            Set<String> allAreas,
            Map<String, Map<String, TimeSeriesMatrix>> columnsIndexByArea,
            String outputDir,
            Map<String, Map<String, LinkedHashSet<String>>> result,
            Map<ConstraintSeriesKey, String> savedSeriesByKey
    ) {
        Set<String> targetAreas = resolveTargetAreas(ctx.area(), allAreas);
        Set<String> allowedLower = toLowerCaseSet(ctx.parameterNames());

        for (String area : targetAreas) {
            Map<String, TimeSeriesMatrix> areaColumns = columnsIndexByArea.getOrDefault(area, Map.of());
            for (String paramName : allowedLower) {
                TimeSeriesMatrix singleColMatrix = areaColumns.get(paramName);
                if (singleColMatrix != null) {
                    String colName = singleColMatrix.columns().getFirst().name();
                    ConstraintSeriesKey key = new ConstraintSeriesKey(
                            constraintsFile,
                            area,
                            colName.toLowerCase(Locale.ROOT)
                    );

                    String savedFile = savedSeriesByKey.computeIfAbsent(key, ignoredKey -> {
                        try {
                            byte[] bytes = nasFileService.getWriter().writeToByteArray(singleColMatrix);
                            return nasFileService.saveMatrixBytesToNas(bytes, colName + ".csv", outputDir);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

                    String clusterKey = area + "_" + ctx.storage().getName();
                    result.computeIfAbsent(area, ignored -> new ConcurrentHashMap<>())
                            .computeIfAbsent(clusterKey, ignored -> new LinkedHashSet<>())
                            .add(savedFile);
                }
            }
        }
    }

    private record ConstraintSeriesKey(Path constraintsFile, String area, String columnName) {
    }

    private Set<String> resolveTargetAreas(String contextArea, Set<String> allAreas) {
        String upperContextArea = contextArea != null ? contextArea.toUpperCase(Locale.ROOT) : "";
        return "OTHERS".equals(upperContextArea) ? allAreas : Set.of(upperContextArea);
    }

    private Set<String> toLowerCaseSet(Set<String> values) {
        return Optional.ofNullable(values)
                .orElse(Set.of())
                .stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private Map<String, Map<String, TimeSeriesMatrix>> indexMatrixColumnsByArea(
            TimeSeriesMatrix matrix,
            Set<String> allAreas
    ) {
        Map<String, Map<String, TimeSeriesMatrix>> index = new HashMap<>();
        allAreas.forEach(area -> index.put(area, new HashMap<>()));

        Map<String, String> suffixToArea = new HashMap<>();
        for (String area : allAreas) {
            suffixToArea.put("_" + area.toLowerCase(Locale.ROOT), area);
        }

        for (var column : matrix.columns()) {
            String clusterName = column.name() != null ? column.name().trim() : "";
            if (!clusterName.isEmpty()) {
                String lower = clusterName.toLowerCase(Locale.ROOT);
                findAreaBySuffix(lower, suffixToArea).ifPresent(area -> index.get(area.toUpperCase(Locale.ROOT)).putIfAbsent(
                        lower,
                        new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn(clusterName, column.values())))
                ));
            }
        }

        return index;
    }

    private Optional<String> findAreaBySuffix(String lowerColumnName, Map<String, String> suffixToArea) {
        return suffixToArea.entrySet().stream()
                .filter(entry -> lowerColumnName.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }


    private Path buildStsConstraintsBasePath() {
        return Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getStsDirectory());
    }


    @Override
    public List<String> createMatrixStsTsFiles(StStorageEntity stsEntity, String horizon) {
        return createMatrixStsTsFiles(stsEntity, horizon, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    private List<String> createMatrixStsTsFiles(StStorageEntity stsEntity, String horizon,
                                                ConcurrentHashMap<Path, TimeSeriesMatrix> matrixCache,
                                                ConcurrentHashMap<Path, byte[]> bytesCache) {
        if (stsEntity == null || stsEntity.getTsPath() == null || stsEntity.getTsPath().isBlank()) {
            return Collections.emptyList();
        }
        Path tsDir = Path.of(stsEntity.getTsPath());

        String outputDir = antaresDataManagerProperties.getStsTsOutputDirectory();
        List<String> saved = new ArrayList<>();
        for (StsTsFile stsTsFile : StsTsFile.requiredFiles()) {
            Path inputPath = stsTsFile.resolve(tsDir);
            TimeSeriesMatrix matrix = getRequiredSeriesMatrix(inputPath, horizon, matrixCache, stsEntity.getGroupe());
            saved.add(saveSeriesMatrix(inputPath, matrix, outputDir, bytesCache));
        }

        return saved;
    }

    private TimeSeriesMatrix getRequiredSeriesMatrix(
            Path inputPath,
            String horizon,
            ConcurrentHashMap<Path, TimeSeriesMatrix> matrixCache,
            String technology
    ) {
        if (!Files.exists(inputPath)) {
            throw BusinessException.builder()
                    .message("Required STS series file not found: {0}")
                    .errorMessageArguments(List.of(inputPath.toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        return matrixCache.computeIfAbsent(inputPath, path -> {
            try {
                return nasFileService.readMatrix(path, horizon, technology, TrajectoryType.STS.name());
            } catch (BusinessException e) {
                String seriesPath = extractSeriesDisplayPath(path);
                throw BusinessException.builder()
                        .message(e.getMessage() + " for series in " + seriesPath)
                        .errorMessageArguments(e.getErrorMessageArguments())
                        .httpStatus(e.getHttpStatus())
                        .build();
            } catch (Exception e) {
                throw BusinessException.builder()
                        .message(e.getMessage())
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        });
    }

    private static String extractSeriesDisplayPath(Path inputPath) {
        String dir = inputPath.getParent().toString().replace("\\", "/");
        int idx = dir.toLowerCase().indexOf("/series/");
        return idx >= 0 ? dir.substring(idx + "/series/".length()) : dir;
    }

    private String saveSeriesMatrix(
            Path inputPath,
            TimeSeriesMatrix matrix,
            String outputDir,
            ConcurrentHashMap<Path, byte[]> bytesCache
    ) {
        try {
            // Serialize once per unique inputPath; reuse bytes when multiple entities share the same tsPath.
            byte[] bytes = bytesCache.computeIfAbsent(inputPath, p -> {
                try {
                    return nasFileService.getWriter().writeToByteArray(matrix);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            String baseName = inputPath.getFileName().toString();
            return nasFileService.saveMatrixBytesToNas(bytes, baseName, outputDir);
        } catch (UncheckedIOException e) {
            throw BusinessException.builder()
                    .message(e.getCause().getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        } catch (IOException e) {
            throw BusinessException.builder()
                    .message(e.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    public record StorageConstraintsContext(
            StStorageEntity storage,
            Path file,
            Set<String> parameterNames,
            String area
    ) {}

    /**
     * Builds the list of {@link StorageConstraintsContext} for all STS storage entities
     * that have constraints enabled, a valid constraints file, and non-empty parameter names.
     */
    private List<StorageConstraintsContext> buildStorageConstraintsContext(StudyEntity studyEntity) {
        Path basePath = buildStsConstraintsBasePath();

        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.STS.name().equals(t.getType()))
                .flatMap(t -> t.getStStorageEntities().stream()
                        .filter(Objects::nonNull)
                        .filter(s -> Boolean.TRUE.equals(s.getConstraintsFlag()))
                        .filter(s -> s.getConstraintsPath() != null)
                        .map(storage -> {
                            Path file = basePath.resolve(storage.getConstraintsPath()).normalize();
                            Set<String> params = Optional.ofNullable(storage.getParameters())
                                    .orElse(List.of())
                                    .stream()
                                    .map(StConstraintsParameterEntity::getName)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
                            return new StorageConstraintsContext(storage, file, params, t.getArea());
                        })
                )
                .filter(ctx -> {
                    if (!Files.exists(ctx.file())) {
                        log.warn("Constraints file not found, skipping: {}", ctx.file());
                        return false;
                    }
                    return true;
                })
                .filter(ctx -> !ctx.parameterNames().isEmpty())
                .toList();
    }
    private Map<String, StsConstraintParameterDTO> mapConstraintParametersFromContext(
            StorageConstraintsContext ctx,
            Set<String> allAreas
    ) {
        List<StConstraintsParameterEntity> params = ctx.storage().getParameters();
        if (params == null || params.isEmpty()) return Collections.emptyMap();

        Set<String> targetAreasLower = resolveTargetAreas(ctx.area(), allAreas).stream()
                .map(area -> area.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Map<String, StsConstraintParameterDTO> result = new LinkedHashMap<>();

        for (StConstraintsParameterEntity param : params) {
            String name = param.getName();
            String zone = param.getZone();
            if (name != null && zone != null && targetAreasLower.contains(zone.toLowerCase(Locale.ROOT))) {
                List<List<Integer>> hours = null;
                if (param.getHours() != null && !param.getHours().isEmpty()) {
                    hours = expandHoursPerOccurrence(param);
                }

                result.put(name, StsConstraintParameterDTO.builder()
                        .variable(param.getVariable())
                        .operator(param.getOperator())
                        .enabled(String.valueOf(param.getEnabled()))
                        .hours(hours)
                        .build());
            }
        }

        return result;
    }
    private List<List<Integer>> expandHoursPerOccurrence(StConstraintsParameterEntity param) {
        Map<Integer, List<Integer>> byOccurrence = new LinkedHashMap<>();

        for (var hour : param.getHours()) {
            if (hour.getOccurrence() == null || hour.getStartHour() == null || hour.getEndHour() == null) {
                throw BusinessException.builder()
                        .message("Invalid hours for constraint {0}: occurrence/start/end must be set")
                        .errorMessageArguments(List.of(param.getName()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            if (hour.getStartHour() > hour.getEndHour()) {
                throw BusinessException.builder()
                        .message("Invalid hours for constraint {0}: start must be <= end")
                        .errorMessageArguments(List.of(param.getName()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            byOccurrence.computeIfAbsent(hour.getOccurrence(), key -> new ArrayList<>())
                    .addAll(IntStream.rangeClosed(hour.getStartHour(), hour.getEndHour()).boxed().toList());
        }

        int maxOccurrence = byOccurrence.keySet().stream().max(Integer::compareTo).orElse(0);
        List<List<Integer>> expanded = new ArrayList<>(maxOccurrence);
        for (int occurrence = 1; occurrence <= maxOccurrence; occurrence++) {
            expanded.add(byOccurrence.getOrDefault(occurrence, List.of()));
        }
        return expanded;
    }


}

