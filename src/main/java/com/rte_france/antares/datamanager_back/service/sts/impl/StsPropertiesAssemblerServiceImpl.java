package com.rte_france.antares.datamanager_back.service.sts.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
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

import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StsPropertiesAssemblerServiceImpl implements StsGenerationAssemblerService {

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final NasFileService nasFileService;
    private final TimeSeriesReader timeSeriesReader;



    @Override
    public Map<String, StsGenerationDTO> assembleStsProperties(StudyEntity studyEntity) {

        Map<String, List<String>> constraintsByArea = createConstraintsTsFiles(studyEntity);
        String horizon = studyEntity.getHorizon();

        // Collect eligible entities first, then process in parallel (each entity triggers heavy I/O)
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

        // Pre-read all unique xlsx files into a cache so entities sharing the same tsPath
        // the same matrix to Arrow when multiple entities share a tsPath.
        ConcurrentHashMap<Path, TimeSeriesMatrix> matrixCache = new ConcurrentHashMap<>();
        ConcurrentHashMap<Path, byte[]> bytesCache = new ConcurrentHashMap<>();

        return eligibleEntities.parallelStream()
                .collect(Collectors.toConcurrentMap(
                        sts -> sts.getArea().toUpperCase() + "_" + sts.getName(),
                        sts -> {
                            StsGenerationDTO dto = StStorageMapper.mapToStsGenerationDTO(sts);
                            dto.setStsTsList(createMatrixStsTsFilesCached(sts, horizon, matrixCache, bytesCache));
                            dto.setStsConstraintsSeriesList(
                                    constraintsByArea.getOrDefault(sts.getArea(), List.of()));
                            return dto;
                        },
                        (existing, replacement) -> existing
                ));
    }


    private Map<String, List<String>> createConstraintsTsFiles(StudyEntity studyEntity) {

        List<StorageConstraintsContext> contexts = buildStorageConstraintsContext(studyEntity);

        Set<String> allAreas = studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.STS.name().equals(t.getType()))
                .flatMap(t -> t.getStStorageEntities().stream()
                        .filter(Objects::nonNull)
                        .filter(s -> Boolean.TRUE.equals(s.getConstraintsFlag()))
                        .map(StStorageEntity::getArea)
                        .filter(Objects::nonNull)
                )
                .collect(Collectors.toSet());

        // ConcurrentHashMap + synchronizedList because the file-entry loop runs in parallel
        Map<String, List<String>> result = new ConcurrentHashMap<>();
        allAreas.forEach(area -> result.put(area, Collections.synchronizedList(new ArrayList<>())));

        // Group contexts by file path to read each xlsx only once
        Map<Path, List<StorageConstraintsContext>> contextsByFile = contexts.stream()
                .collect(Collectors.groupingBy(StorageConstraintsContext::file));

        String horizon = studyEntity.getHorizon();

        String outputDir = antaresDataManagerProperties.getStsTsOutputDirectory();

        // Bytes cache keyed on the column name: if the same column appears across multiple contexts
        // referencing the same file, we serialize it once and reuse the Arrow bytes.
        ConcurrentHashMap<String, byte[]> constraintsBytesCache = new ConcurrentHashMap<>();

        // Each file entry is independent — parallelize to overlap xlsx reads and NAS writes
        contextsByFile.entrySet().parallelStream().forEach(fileEntry -> {
            Path file = fileEntry.getKey();
            TimeSeriesMatrix matrix = readConstraintsMatrix(file, horizon);

            for (StorageConstraintsContext ctx : fileEntry.getValue()) {
                Set<String> targetAreas = "OTHERS".equalsIgnoreCase(ctx.area())
                        ? allAreas
                        : Set.of(ctx.area());

                try {
                    Map<String, List<TimeSeriesMatrix>> splitByArea = splitMatrixColumnsByArea(
                            matrix, ctx.parameterNames(), targetAreas);

                    for (var entry : splitByArea.entrySet()) {
                        String area = entry.getKey();
                        for (TimeSeriesMatrix singleColMatrix : entry.getValue()) {
                            String colName = singleColMatrix.columns().getFirst().name();
                            byte[] bytes = constraintsBytesCache.computeIfAbsent(colName, k -> {
                                try {
                                    return nasFileService.getWriter().writeToByteArray(singleColMatrix);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                            String savedFile = nasFileService.saveMatrixBytesToNas(bytes, colName + ".csv", outputDir);
                            result.get(area).add(savedFile);
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });

        return result;
    }

    private TimeSeriesMatrix readConstraintsMatrix(Path file, String horizon) {
        String fileName = file.getFileName().toString().toLowerCase();
        if (!fileName.endsWith(".xlsx")) {
            throw BusinessException.builder()
                    .message("Only .xlsx supported")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        try {
            TimeSeriesMatrix matrix = timeSeriesReader.readFromXlsx(file, horizon);
            if (matrix.columns().isEmpty()) {
                throw TechnicalException.builder()
                        .message("Matrix is empty: " + file.getFileName())
                        .build();
            }
            return matrix;
        } catch (BusinessException | TechnicalException e) {
            throw e;
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    private Map<String, List<TimeSeriesMatrix>> splitMatrixColumnsByArea(
            TimeSeriesMatrix matrix,
            Set<String> paramNames,
            Set<String> targetAreas
    ) {

        Map<String, List<TimeSeriesMatrix>> result = new HashMap<>();
        targetAreas.forEach(a -> result.put(a, new ArrayList<>()));

        // Pre-compute lowercase area suffixes to avoid repeated toLowerCase() in inner loop
        Map<String, String> suffixToArea = new HashMap<>();
        for (String area : targetAreas) {
            suffixToArea.put("_" + area.toLowerCase(), area);
        }

        // Pre-compute lowercase param names for matching
        Set<String> allowedLower = paramNames != null
                ? paramNames.stream().map(String::toLowerCase).collect(Collectors.toSet())
                : Set.of();

        for (var column : matrix.columns()) {

            String clusterName = column.name() != null ? column.name().trim() : "";
            if (clusterName.isEmpty()) continue;

            String lower = clusterName.toLowerCase();

            // Check if this column is allowed by param names
            if (!allowedLower.contains(lower)) continue;

            for (var suffixEntry : suffixToArea.entrySet()) {

                if (!lower.endsWith(suffixEntry.getKey())) continue;
                String area = suffixEntry.getValue();

                TimeSeriesMatrix singleColMatrix = new TimeSeriesMatrix(
                        List.of(new TimeSeriesMatrixColumn(clusterName, column.values()))
                );
                result.get(area).add(singleColMatrix);
            }
        }

        return result;
    }

    private Path buildStsConstraintsBasePath() {
        return Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getStsDirectory());
    }


    @Override
    public List<String> createMatrixStsTsFiles(StStorageEntity stsEntity, String horizon) {
        return createMatrixStsTsFilesCached(stsEntity, horizon, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    private List<String> createMatrixStsTsFilesCached(StStorageEntity stsEntity, String horizon,
                                                       ConcurrentHashMap<Path, TimeSeriesMatrix> matrixCache,
                                                       ConcurrentHashMap<Path, byte[]> bytesCache) {
        if (stsEntity == null || stsEntity.getTsPath() == null || stsEntity.getTsPath().isBlank()) {
            return Collections.emptyList();
        }
        Path tsDir = Path.of(stsEntity.getTsPath());

        String outputDir = antaresDataManagerProperties.getStsTsOutputDirectory();
        List<String> saved = new ArrayList<>();
        for (StsTsFile stsTsFile : StsTsFile.REQUIRED) {
            Path inputPath = stsTsFile.resolve(tsDir);

            if (!Files.exists(inputPath)) {
                throw BusinessException.builder()
                        .message("Required STS series file not found: {0}")
                        .errorMessageArguments(List.of(inputPath.toString()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            TimeSeriesMatrix matrix = matrixCache.computeIfAbsent(inputPath, path -> {
                try {
                    return nasFileService.readMatrix(path, horizon);
                } catch (BusinessException e) {
                    throw e;
                } catch (Exception e) {
                    throw BusinessException.builder()
                            .message(e.getMessage())
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            });

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
                saved.add(nasFileService.saveMatrixBytesToNas(bytes, baseName, outputDir));
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

        return saved;
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
                            Path file = basePath.resolve(storage.getConstraintsPath());
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
                .collect(Collectors.toList());
    }


}
