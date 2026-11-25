package com.rte_france.antares.datamanager_back.service.misc.impl;

import com.rte_france.antares.datamanager_back.util.AreaNaming;
import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaKey;
import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.util.ExecutionTime;
import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.MiscGenMapper;
import com.rte_france.antares.datamanager_back.repository.model.MiscClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.MiscGroupEnum;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.misc.MiscGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class MiscGenerationAssemblerServiceImpl implements MiscGenerationAssemblerService {

    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final NasFileService nasFileService;
    private final TimeSeriesReader timeSeriesReader;
    private final MiscFileProcessorServiceImpl miscFileProcessorService;

    public MiscGenerationAssemblerServiceImpl(
            MiscFileProcessorServiceImpl miscFileProcessorService,
            NasFileService nasFileService,
            AntaresDataManagerProperties antaresDataManagerProperties,
            TimeSeriesReader timeSeriesReader) {
        this.miscFileProcessorService = miscFileProcessorService;
        this.nasFileService = nasFileService;
        this.antaresDataManagerProperties = antaresDataManagerProperties;
        this.timeSeriesReader = timeSeriesReader;
    }

    private static final AreaKey OTHERS_KEY = AreaKey.of(AreaNaming.OTHERS);
    private static final AreaKey FR_KEY = AreaKey.of("FR");

    @ExecutionTime("MISC")
    @Override
    public Map<AreaKey, List<MiscGenerationDTO>> assembleMiscProperties(StudyEntity studyEntity) {
        List<Path> generatedFiles = createSplitMiscGenFiles(studyEntity);
        Map<AreaKey, List<String>> filesByArea = buildFilesByArea(generatedFiles);
        Set<AreaKey> areasWithSpecificCapacity = getAreasWithSpecificCapacity(studyEntity);

        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.MISC_CAPACITY.name().equals(t.getType()))
                .flatMap(trajectory -> filterMiscCapacityEntities(trajectory, areasWithSpecificCapacity))
                .filter(c -> c.getCapacityByYear() != null && c.getCapacityByYear().signum() > 0)
                // A blank area can't be a study area: skip it before building the key
                .filter(c -> !c.getArea().isBlank())
                .collect(Collectors.groupingBy(
                        misc -> AreaKey.of(misc.getArea()),
                        Collectors.mapping(
                                misc -> mapToDto(misc, filesByArea),
                                Collectors.toList()
                        )
                ));
    }

    private MiscGenerationDTO mapToDto(MiscClusterCapacityEntity misc, Map<AreaKey, List<String>> filesByArea) {
        MiscGenerationDTO dto = MiscGenMapper.mapToMiscGenerationDTO(misc);
        dto.setMiscGenTsList(filesByArea.getOrDefault(AreaKey.of(misc.getArea()), Collections.emptyList()));
        return dto;
    }

    private Set<AreaKey> getAreasWithSpecificCapacity(StudyEntity studyEntity) {
        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.MISC_CAPACITY.name().equals(t.getType()))
                .filter(t -> t.getArea() != null && !t.getArea().isBlank() && !AreaNaming.isOthers(t.getArea()))
                .map(t -> AreaKey.of(t.getArea()))
                .collect(Collectors.toSet());
    }

    private Stream<MiscClusterCapacityEntity> filterMiscCapacityEntities(
            TrajectoryEntity trajectory,
            Set<AreaKey> areasWithSpecificCapacity) {
        List<MiscClusterCapacityEntity> entities = trajectory.getMiscClusterCapacityEntities();
        if (entities == null) {
            return Stream.empty();
        }
        boolean isOthersTrajectory = AreaNaming.isOthers(trajectory.getArea());

        return entities.stream()
                .filter(entity -> shouldIncludeMiscCapacityEntity(entity, isOthersTrajectory, areasWithSpecificCapacity));
    }

    private boolean shouldIncludeMiscCapacityEntity(
            MiscClusterCapacityEntity entity,
            boolean isOthersTrajectory,
            Set<AreaKey> areasWithSpecificCapacity) {
        if (!isOthersTrajectory) {
            return true;
        }
        String entityArea = entity.getArea();
        return entityArea == null || entityArea.isBlank() || !areasWithSpecificCapacity.contains(AreaKey.of(entityArea));
    }

    private List<Path> createSplitMiscGenFiles(StudyEntity study) {
        // 1. Group trajectories by type and by area (area)
        Map<AreaKey, TrajectoryEntity> miscCapacityByArea = mapTrajectoriesByArea(study, TrajectoryType.MISC_CAPACITY);
        Map<AreaKey, TrajectoryEntity> miscLoadByArea = mapTrajectoriesByArea(study, TrajectoryType.MISC_LOAD);
        List<Path> allGeneratedFiles = new ArrayList<>();
        Set<AreaKey> processedAreas = new HashSet<>();

        // 2. Specific areas treatment
        var areas = nonOtherAreas(miscCapacityByArea.keySet(), miscLoadByArea.keySet());
        for (AreaKey area : areas) {
            TrajectoryEntity capacityTraj = miscCapacityByArea.get(containsOnlyOtherCapacity(miscCapacityByArea.keySet()) ? OTHERS_KEY : area);
            TrajectoryEntity loadTraj = resolveLoadTrajectoryForArea(area, miscLoadByArea);
            if (capacityTraj != null) {
                processedAreas.add(area);
            }
            if (loadTraj != null && loadTraj.getFileName() != null) {
                allGeneratedFiles.addAll(processTrajectoryPair(study, area.value(), capacityTraj, loadTraj.getFileName(), Set.of()));
            }
        }

        // 3.OTHERS case
        processOthers(study, miscCapacityByArea, miscLoadByArea, processedAreas, allGeneratedFiles);
        return allGeneratedFiles;
    }

    private static boolean containsOnlyOtherCapacity(Set<AreaKey> capacityKeys) {
        return capacityKeys.size() == 1 && capacityKeys.contains(OTHERS_KEY);
    }

    private void processOthers(StudyEntity study, Map<AreaKey, TrajectoryEntity> capMap, Map<AreaKey, TrajectoryEntity> loadMap, Set<AreaKey> processed, List<Path> files) {
        TrajectoryEntity capacityOthers = capMap.get(OTHERS_KEY);
        TrajectoryEntity loadOthers = loadMap.get(OTHERS_KEY);
        if (capacityOthers != null && loadOthers != null && loadOthers.getFileName() != null) {
            files.addAll(processTrajectoryPair(study, AreaNaming.OTHERS, capacityOthers, loadOthers.getFileName(), processed));
        }
    }

    private TrajectoryEntity resolveLoadTrajectoryForArea(AreaKey area, Map<AreaKey, TrajectoryEntity> miscLoadByArea) {
        TrajectoryEntity directLoadTrajectory = miscLoadByArea.get(area);
        if (directLoadTrajectory != null) {
            return directLoadTrajectory;
        }
        return FR_KEY.equals(area) ? null : miscLoadByArea.get(OTHERS_KEY);
    }

    private List<Path> processTrajectoryPair(StudyEntity study, String area, TrajectoryEntity capacityTraj, String loadFileName, Set<AreaKey> processedAreas) {
        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupToAreas = resolveGroupToAreas(study, area, capacityTraj);
        if (loadFileName == null || groupToAreas.isEmpty()) {
            return Collections.emptyList();
        }

        List<Path> generatedFiles = new ArrayList<>();
        Map<AreaKey, double[]> weightedOtherSeriesByArea = new LinkedHashMap<>();
        Map<AreaKey, Double> totalOtherCapacityByArea = new LinkedHashMap<>();
        Path baseMiscLoadPath = Path.of(antaresDataManagerProperties.getNasDirectory()).resolve(antaresDataManagerProperties.getTrajectoryFilePath()).resolve(antaresDataManagerProperties.getMiscLoadDirectory()).resolve(loadFileName);
        Map<Path, TimeSeriesMatrix> matrixCache = new HashMap<>();

        for (Map.Entry<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> entry : groupToAreas.entrySet()) {
            processGroupEntry(entry, processedAreas, baseMiscLoadPath, study.getHorizon(), capacityTraj, matrixCache, weightedOtherSeriesByArea, totalOtherCapacityByArea, generatedFiles);
        }

        try {
            generatedFiles.addAll(writeAggregatedOtherSeries(weightedOtherSeriesByArea, totalOtherCapacityByArea));
        } catch (IOException e) {
            log.error("Error writing aggregated misc other series for area {}", area, e);
        }
        return generatedFiles;
    }

    private void processGroupEntry(Map.Entry<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> entry, Set<AreaKey> processedAreas, Path basePath, String horizon, TrajectoryEntity capacityTraj, Map<Path, TimeSeriesMatrix> cache, Map<AreaKey, double[]> weightedSum, Map<AreaKey, Double> totalCap, List<Path> files) {
        MiscFileProcessorServiceImpl.GroupClusterKey key = entry.getKey();
        String normalizedGroup = MiscGroupEnum.normalizeForGenerator(key.groupe());
        Set<AreaKey> areasToProcess = toAreaKeys(entry.getValue());
        areasToProcess.removeAll(processedAreas);

        if (areasToProcess.isEmpty()) return;

        Path tsFilePath = MiscFileProcessorServiceImpl.getLoadFactorByGroupPath(horizon, basePath, key);
        if (!Files.exists(tsFilePath)) {
            log.debug("Load factor file not found for path: {}", tsFilePath);
            return;
        }

        try {
            TimeSeriesMatrix matrix = cache.computeIfAbsent(tsFilePath, p -> loadMatrixUnchecked(p, horizon));
            if (matrix == null) return;

            if (MiscGroupEnum.OTHER.value().equals(normalizedGroup)) {
                aggregateSeries(matrix, key, areasToProcess, capacityTraj, weightedSum, totalCap);
            } else {
                files.addAll(splitMatrixColumns(matrix, areasToProcess, normalizedGroup));
            }
        } catch (TechnicalException | IOException e) {
            log.error("Error processing file {}", tsFilePath, e);
        }
    }

    private TimeSeriesMatrix loadMatrixUnchecked(Path path, String horizon) {
        try {
            return readMatrix(path, horizon);
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Failed to read matrix file: " + path)
                    .cause(e)
                    .build();
        }
    }

    private void aggregateSeries(TimeSeriesMatrix matrix, MiscFileProcessorServiceImpl.GroupClusterKey key, Set<AreaKey> areas, TrajectoryEntity capacityTrajectory, Map<AreaKey, double[]> weightedSumByArea, Map<AreaKey, Double> totalCapacityByArea) {
        Map<AreaKey, double[]> areaSeries = extractSeriesByArea(matrix, areas);
        for (Map.Entry<AreaKey, double[]> areaSeriesEntry : areaSeries.entrySet()) {
            AreaKey area = areaSeriesEntry.getKey();
            double[] values = areaSeriesEntry.getValue();
            double capacity = getCapacityForAreaGroupCluster(capacityTrajectory, area, key);

            if (capacity <= 0d) continue;

            double[] existing = weightedSumByArea.get(area);
            if (existing == null) {
                double[] weightedValues = new double[values.length];
                for (int i = 0; i < values.length; i++) {
                    weightedValues[i] = values[i] * capacity;
                }
                weightedSumByArea.put(area, weightedValues);
            } else {
                validateAndSumArrays(existing, values, capacity, area);
            }
            totalCapacityByArea.merge(area, capacity, Double::sum);
        }
    }

    private void validateAndSumArrays(double[] existing, double[] values, double capacity, AreaKey area) {
        if (existing.length != values.length) {
            throw TechnicalException.builder()
                    .message("Cannot aggregate MISC load factor series with different row counts for area " + area.value())
                    .build();
        }
        for (int i = 0; i < existing.length; i++) {
            existing[i] += values[i] * capacity;
        }
    }

    private List<Path> writeAggregatedOtherSeries(Map<AreaKey, double[]> weightedSumByArea, Map<AreaKey, Double> totalCapacityByArea) throws IOException {
        if (weightedSumByArea.isEmpty()) return Collections.emptyList();

        String outputDir = antaresDataManagerProperties.getMiscGenTsOutputDirectory();
        List<Path> generatedFiles = new ArrayList<>();

        for (Map.Entry<AreaKey, double[]> entry : weightedSumByArea.entrySet()) {
            AreaKey area = entry.getKey();
            Double totalCapacity = totalCapacityByArea.get(area);
            if (totalCapacity == null || totalCapacity <= 0d) continue;

            double[] weightedSum = entry.getValue();
            double[] weightedAverage = new double[weightedSum.length];
            for (int i = 0; i < weightedSum.length; i++) {
                weightedAverage[i] = weightedSum[i] / totalCapacity;
            }

            TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn(area.value(), weightedAverage)));
            String outputFileName = nasFileService.saveMatrixToNas(matrix, area.value() + "_" + MiscGroupEnum.OTHER.value(), outputDir);
            generatedFiles.add(Path.of(outputDir).resolve(outputFileName));
        }
        return generatedFiles;
    }

    private double getCapacityForAreaGroupCluster(TrajectoryEntity capacityTrajectory, AreaKey area, MiscFileProcessorServiceImpl.GroupClusterKey groupClusterKey) {
        if (capacityTrajectory == null || capacityTrajectory.getMiscClusterCapacityEntities() == null) {
            return 0d;
        }
        return capacityTrajectory.getMiscClusterCapacityEntities().stream()
                .filter(Objects::nonNull)
                .filter(e -> e.getCapacityByYear() != null && e.getCapacityByYear().signum() > 0)
                .filter(e -> e.getArea() != null && !e.getArea().isBlank() && area.equals(AreaKey.of(e.getArea())))
                .filter(e -> e.getGroupe() != null && e.getGroupe().equalsIgnoreCase(groupClusterKey.groupe()))
                .filter(e -> isSameCluster(e.getCluster(), groupClusterKey.cluster()))
                .mapToDouble(e -> e.getCapacityByYear().doubleValue())
                .sum();
    }

    private boolean isSameCluster(String entityCluster, String expectedCluster) {
        return expectedCluster == null || expectedCluster.isBlank() || (entityCluster != null && entityCluster.equalsIgnoreCase(expectedCluster));
    }

    private Map<AreaKey, double[]> extractSeriesByArea(TimeSeriesMatrix matrix, Set<AreaKey> areas) {
        Map<AreaKey, double[]> seriesByArea = new LinkedHashMap<>();
        for (TimeSeriesMatrixColumn column : matrix.columns()) {
            columnArea(column, areas).ifPresent(area -> seriesByArea.put(area, column.values()));
        }
        return seriesByArea;
    }

    // The area of a series column, if it is one of the given areas
    private Optional<AreaKey> columnArea(TimeSeriesMatrixColumn column, Set<AreaKey> areas) {
        if (column.name() == null || column.name().isBlank()) {
            return Optional.empty();
        }
        AreaKey area = AreaKey.of(column.name());
        return areas.contains(area) ? Optional.of(area) : Optional.empty();
    }

    public List<Path> splitMiscGenLoadFiles(Path file, Set<String> areas, String horizon, String groupName) throws IOException {
        TimeSeriesMatrix matrix = readMatrix(file, horizon);
        if (matrix == null || matrix.columns().isEmpty()) {
            return Collections.emptyList();
        }
        return splitMatrixColumns(matrix, toAreaKeys(areas), groupName);
    }

    private List<Path> splitMatrixColumns(TimeSeriesMatrix matrix, Set<AreaKey> areas, String groupName) throws IOException {
        List<Path> generatedFiles = new ArrayList<>();
        String outputDir = antaresDataManagerProperties.getMiscGenTsOutputDirectory();

        for (TimeSeriesMatrixColumn column : matrix.columns()) {
            Optional<AreaKey> area = columnArea(column, areas);
            if (area.isEmpty()) continue;

            TimeSeriesMatrix singleColMatrix = new TimeSeriesMatrix(List.of(column));
            String areaName = area.get().value();
            String baseName = groupName != null && !groupName.isEmpty() ? areaName + "_" + groupName : areaName;
            String outputFileName = nasFileService.saveMatrixToNas(singleColMatrix, baseName, outputDir);
            generatedFiles.add(Path.of(outputDir).resolve(outputFileName));
        }
        return generatedFiles;
    }

    private Map<AreaKey, TrajectoryEntity> mapTrajectoriesByArea(StudyEntity study, TrajectoryType type) {
        return study.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> type.name().equals(t.getType()))
                .filter(t -> t.getArea() != null && !t.getArea().isBlank())
                .collect(Collectors.toMap(
                        t -> AreaKey.of(t.getArea()),
                        t -> t,
                        (t1, t2) -> t1
                ));
    }

    private Set<AreaKey> nonOtherAreas(Set<AreaKey> capacityAreas, Set<AreaKey> loadAreas) {
        if (containsOnlyOtherCapacity(capacityAreas) && !loadAreas.contains(OTHERS_KEY) && !loadAreas.isEmpty()) {
            return loadAreas;
        }
        Set<AreaKey> resultCapacity = new HashSet<>(capacityAreas);
        resultCapacity.remove(OTHERS_KEY);
        return resultCapacity;
    }

    private Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> resolveGroupToAreas(StudyEntity study, String area, TrajectoryEntity capacityTrajectory) {
        if (capacityTrajectory != null && capacityTrajectory.getId() != null) {
            return miscFileProcessorService.getAreasByGroupClusterByTrajectoryId(capacityTrajectory.getId());
        }
        return miscFileProcessorService.getAreasByGroupClusterByStudyId(study.getId(), area);
    }

    // A blank name can't match a series column: skip it before building the key
    private Set<AreaKey> toAreaKeys(Collection<String> areas) {
        return areas.stream()
                .filter(area -> area != null && !area.isBlank())
                .map(AreaKey::of)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private TimeSeriesMatrix readMatrix(Path file, String horizon) throws IOException {
        String fileName = file.getFileName().toString().toLowerCase();
        TimeSeriesReader localReader = (this.timeSeriesReader != null) ? this.timeSeriesReader : new TimeSeriesReader();
        try {
            if (fileName.endsWith(".xlsx")) return localReader.readFromXlsx(file, horizon, true);
            if (fileName.endsWith(".txt") || fileName.endsWith(".csv")) return localReader.readFromTxt(file);
            return null;
        } catch (RuntimeException ex) {
            throw new IOException(ex);
        }
    }

    private Map<AreaKey, List<String>> buildFilesByArea(List<Path> generatedFiles) {
        Map<AreaKey, List<String>> filesByArea = new HashMap<>();
        for (Path path : generatedFiles) {
            String fileName = path.getFileName().toString();
            // Expected filename: AREA_GROUP.UUID.arrow or AREA.UUID.arrow
            int delimiterIndex = fileName.indexOf('_');
            if (delimiterIndex == -1) {
                delimiterIndex = fileName.indexOf('.');
            }
            String area = (delimiterIndex == -1) ? fileName : fileName.substring(0, delimiterIndex);
            filesByArea.computeIfAbsent(AreaKey.of(area), ignored -> new ArrayList<>()).add(fileName);
        }
        return filesByArea;
    }
}