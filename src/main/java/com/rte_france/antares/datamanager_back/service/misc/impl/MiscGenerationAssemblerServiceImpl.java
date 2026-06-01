package com.rte_france.antares.datamanager_back.service.misc.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
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

    private static final String OTHER_AREA = "OTHERS";

    @Override
    public Map<String, List<MiscGenerationDTO>> assembleMiscProperties(StudyEntity studyEntity) {
        List<Path> generatedFiles = createSplitMiscGenFiles(studyEntity);
        Map<String, List<String>> filesByArea = buildFilesByArea(generatedFiles);
        Set<String> areasWithSpecificCapacity = getAreasWithSpecificCapacity(studyEntity);

        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.MISC_CAPACITY.name().equals(t.getType()))
                .flatMap(trajectory -> filterMiscCapacityEntities(trajectory, areasWithSpecificCapacity))
                .filter(c -> c.getCapacityByYear() != null && c.getCapacityByYear().signum() > 0)
                .collect(Collectors.groupingBy(
                        misc -> misc.getArea().toUpperCase(),
                        Collectors.mapping(
                                misc -> mapToDto(misc, filesByArea),
                                Collectors.toList()
                        )
                ));
    }

    private MiscGenerationDTO mapToDto(MiscClusterCapacityEntity misc, Map<String, List<String>> filesByArea) {
        MiscGenerationDTO dto = MiscGenMapper.mapToMiscGenerationDTO(misc);
        dto.setMiscGenTsList(filesByArea.getOrDefault(misc.getArea().toUpperCase(), Collections.emptyList()));
        return dto;
    }

    private Set<String> getAreasWithSpecificCapacity(StudyEntity studyEntity) {
        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.MISC_CAPACITY.name().equals(t.getType()))
                .filter(t -> t.getArea() != null && !OTHER_AREA.equalsIgnoreCase(t.getArea()))
                .map(t -> t.getArea().toUpperCase())
                .collect(Collectors.toSet());
    }

    private Stream<MiscClusterCapacityEntity> filterMiscCapacityEntities(
            TrajectoryEntity trajectory,
            Set<String> areasWithSpecificCapacity) {
        List<MiscClusterCapacityEntity> entities = trajectory.getMiscClusterCapacityEntities();
        if (entities == null) {
            return Stream.empty();
        }
        String trajectoryArea = trajectory.getArea() != null ? trajectory.getArea().toUpperCase() : "";
        boolean isOthersTrajectory = OTHER_AREA.equalsIgnoreCase(trajectoryArea);

        return entities.stream()
                .filter(entity -> shouldIncludeMiscCapacityEntity(entity, isOthersTrajectory, areasWithSpecificCapacity));
    }

    private boolean shouldIncludeMiscCapacityEntity(
            MiscClusterCapacityEntity entity,
            boolean isOthersTrajectory,
            Set<String> areasWithSpecificCapacity) {
        if (!isOthersTrajectory) {
            return true;
        }
        String entityArea = entity.getArea() != null ? entity.getArea().toUpperCase() : "";
        return !areasWithSpecificCapacity.contains(entityArea);
    }

    private List<Path> createSplitMiscGenFiles(StudyEntity study) {
        // 1. Group trajectories by type and by area (area)
        Map<String, TrajectoryEntity> miscCapacityByArea = mapTrajectoriesByArea(study, TrajectoryType.MISC_CAPACITY);
        Map<String, TrajectoryEntity> miscLoadByArea = mapTrajectoriesByArea(study, TrajectoryType.MISC_LOAD);
        List<Path> allGeneratedFiles = new ArrayList<>();
        Set<String> processedAreas = new HashSet<>();

        // 2. Specific areas treatment
        var areas = nonOtherAreas(miscCapacityByArea.keySet(), miscLoadByArea.keySet());
        for (String area : areas) {
            TrajectoryEntity capacityTraj = containsOnlyOtherCapacity(miscCapacityByArea.keySet()) ? miscCapacityByArea.get(OTHER_AREA) : miscCapacityByArea.get(area);
            TrajectoryEntity loadTraj = resolveLoadTrajectoryForArea(area, miscLoadByArea);
            if (capacityTraj != null) {
                processedAreas.add(area);
            }
            if (loadTraj != null && loadTraj.getFileName() != null) {
                allGeneratedFiles.addAll(processTrajectoryPair(study, area, capacityTraj, loadTraj.getFileName(), Set.of()));
            }
        }

        // 3.OTHERS case
        processOthers(study, miscCapacityByArea, miscLoadByArea, processedAreas, allGeneratedFiles);
        return allGeneratedFiles;
    }

    public static boolean containsOnlyOtherCapacity(Set<String> capacityKeys) {
        if (capacityKeys == null || capacityKeys.isEmpty()) {
            return false;
        }
        return capacityKeys.size() == 1 &&
                capacityKeys.iterator().next().trim().equalsIgnoreCase(OTHER_AREA);
    }

    private void processOthers(StudyEntity study, Map<String, TrajectoryEntity> capMap, Map<String, TrajectoryEntity> loadMap, Set<String> processed, List<Path> files) {
        TrajectoryEntity capacityOthers = capMap.get(OTHER_AREA.toUpperCase());
        TrajectoryEntity loadOthers = loadMap.get(OTHER_AREA.toUpperCase());
        if (capacityOthers != null && loadOthers != null && loadOthers.getFileName() != null) {
            files.addAll(processTrajectoryPair(study, OTHER_AREA, capacityOthers, loadOthers.getFileName(), processed));
        }
    }

    private TrajectoryEntity resolveLoadTrajectoryForArea(String area, Map<String, TrajectoryEntity> miscLoadByArea) {
        TrajectoryEntity directLoadTrajectory = miscLoadByArea.get(area.toUpperCase());
        if (directLoadTrajectory != null) {
            return directLoadTrajectory;
        }
        return "FR".equalsIgnoreCase(area) ? null : miscLoadByArea.get(OTHER_AREA.toUpperCase());
    }

    private List<Path> processTrajectoryPair(StudyEntity study, String area, TrajectoryEntity capacityTraj, String loadFileName, Set<String> processedAreas) {
        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupToAreas = resolveGroupToAreas(study, area, capacityTraj);
        if (loadFileName == null || groupToAreas.isEmpty()) {
            return Collections.emptyList();
        }

        List<Path> generatedFiles = new ArrayList<>();
        Map<String, double[]> weightedOtherSeriesByArea = new LinkedHashMap<>();
        Map<String, Double> totalOtherCapacityByArea = new LinkedHashMap<>();
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

    private void processGroupEntry(Map.Entry<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> entry, Set<String> processedAreas, Path basePath, String horizon, TrajectoryEntity capacityTraj, Map<Path, TimeSeriesMatrix> cache, Map<String, double[]> weightedSum, Map<String, Double> totalCap, List<Path> files) {
        MiscFileProcessorServiceImpl.GroupClusterKey key = entry.getKey();
        String normalizedGroup = MiscGroupEnum.normalizeForGenerator(key.groupe());
        Set<String> areasToProcess = entry.getValue().stream().filter(a -> !processedAreas.contains(a.toUpperCase())).collect(Collectors.toSet());

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

    private void aggregateSeries(TimeSeriesMatrix matrix, MiscFileProcessorServiceImpl.GroupClusterKey key, Set<String> areas, TrajectoryEntity capacityTrajectory, Map<String, double[]> weightedSumByArea, Map<String, Double> totalCapacityByArea) throws IOException {
        Map<String, double[]> areaSeries = extractSeriesByArea(matrix, areas);
        for (Map.Entry<String, double[]> areaSeriesEntry : areaSeries.entrySet()) {
            String area = areaSeriesEntry.getKey();
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

    private void validateAndSumArrays(double[] existing, double[] values, double capacity, String area) {
        if (existing.length != values.length) {
            throw TechnicalException.builder()
                    .message("Cannot aggregate MISC load factor series with different row counts for area " + area)
                    .build();
        }
        for (int i = 0; i < existing.length; i++) {
            existing[i] += values[i] * capacity;
        }
    }

    private List<Path> writeAggregatedOtherSeries(Map<String, double[]> weightedSumByArea, Map<String, Double> totalCapacityByArea) throws IOException {
        if (weightedSumByArea.isEmpty()) return Collections.emptyList();

        String outputDir = antaresDataManagerProperties.getMiscGenTsOutputDirectory();
        List<Path> generatedFiles = new ArrayList<>();

        for (Map.Entry<String, double[]> entry : weightedSumByArea.entrySet()) {
            String area = entry.getKey();
            Double totalCapacity = totalCapacityByArea.get(area);
            if (totalCapacity == null || totalCapacity <= 0d) continue;

            double[] weightedSum = entry.getValue();
            double[] weightedAverage = new double[weightedSum.length];
            for (int i = 0; i < weightedSum.length; i++) {
                weightedAverage[i] = weightedSum[i] / totalCapacity;
            }

            TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn(area, weightedAverage)));
            String outputFileName = nasFileService.saveMatrixToNas(matrix, area.toUpperCase() + "_" + MiscGroupEnum.OTHER.value(), outputDir);
            generatedFiles.add(Path.of(outputDir).resolve(outputFileName));
        }
        return generatedFiles;
    }

    private double getCapacityForAreaGroupCluster(TrajectoryEntity capacityTrajectory, String area, MiscFileProcessorServiceImpl.GroupClusterKey groupClusterKey) {
        if (capacityTrajectory == null || capacityTrajectory.getMiscClusterCapacityEntities() == null) {
            return 0d;
        }
        return capacityTrajectory.getMiscClusterCapacityEntities().stream()
                .filter(Objects::nonNull)
                .filter(e -> e.getCapacityByYear() != null && e.getCapacityByYear().signum() > 0)
                .filter(e -> e.getArea() != null && e.getArea().equalsIgnoreCase(area))
                .filter(e -> e.getGroupe() != null && e.getGroupe().equalsIgnoreCase(groupClusterKey.groupe()))
                .filter(e -> isSameCluster(e.getCluster(), groupClusterKey.cluster()))
                .mapToDouble(e -> e.getCapacityByYear().doubleValue())
                .sum();
    }

    private boolean isSameCluster(String entityCluster, String expectedCluster) {
        return expectedCluster == null || expectedCluster.isBlank() || (entityCluster != null && entityCluster.equalsIgnoreCase(expectedCluster));
    }

    private Map<String, double[]> extractSeriesByArea(TimeSeriesMatrix matrix, Set<String> areas) {
        Set<String> allowedAreas = normalizeAreas(areas);
        Map<String, double[]> seriesByArea = new LinkedHashMap<>();

        for (TimeSeriesMatrixColumn column : matrix.columns()) {
            String colName = column.name() != null ? column.name().trim() : "";
            if (!colName.isEmpty() && allowedAreas.contains(colName.toLowerCase())) {
                seriesByArea.put(colName.toUpperCase(), column.values());
            }
        }
        return seriesByArea;
    }

    public List<Path> splitMiscGenLoadFiles(Path file, Set<String> areas, String horizon, String groupName) throws IOException {
        TimeSeriesMatrix matrix = readMatrix(file, horizon);
        if (matrix == null || matrix.columns().isEmpty()) {
            return Collections.emptyList();
        }
        return splitMatrixColumns(matrix, areas, groupName);
    }

    private List<Path> splitMatrixColumns(TimeSeriesMatrix matrix, Set<String> areas, String groupName) throws IOException {
        Set<String> allowedAreas = normalizeAreas(areas);
        List<Path> generatedFiles = new ArrayList<>();
        String outputDir = antaresDataManagerProperties.getMiscGenTsOutputDirectory();

        for (TimeSeriesMatrixColumn column : matrix.columns()) {
            String colName = column.name() != null ? column.name().trim() : "";
            if (colName.isEmpty() || !allowedAreas.contains(colName.toLowerCase())) continue;

            TimeSeriesMatrix singleColMatrix = new TimeSeriesMatrix(List.of(column));
            String baseName = groupName != null && !groupName.isEmpty() ? colName.toUpperCase() + "_" + groupName : colName.toUpperCase();
            String outputFileName = nasFileService.saveMatrixToNas(singleColMatrix, baseName, outputDir);
            generatedFiles.add(Path.of(outputDir).resolve(outputFileName));
        }
        return generatedFiles;
    }

    private Map<String, TrajectoryEntity> mapTrajectoriesByArea(StudyEntity study, TrajectoryType type) {
        return study.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> type.name().equals(t.getType()))
                .filter(t -> t.getArea() != null)
                .collect(Collectors.toMap(
                        t -> t.getArea().toUpperCase(),
                        t -> t,
                        (t1, t2) -> t1
                ));
    }

    private Set<String> nonOtherAreas(Set<String> capacityAreas, Set<String> loadAreas) {
        Set<String> result = new HashSet<>(capacityAreas);
        if (containsOnlyOtherCapacity(result) && !loadAreas.isEmpty()) {
            return loadAreas;
        }
        result.remove(OTHER_AREA.toUpperCase());
        return result;
    }

    private Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> resolveGroupToAreas(StudyEntity study, String area, TrajectoryEntity capacityTrajectory) {
        if (capacityTrajectory != null && capacityTrajectory.getId() != null) {
            return miscFileProcessorService.getAreasByGroupClusterByTrajectoryId(capacityTrajectory.getId());
        }
        return miscFileProcessorService.getAreasByGroupClusterByStudyId(study.getId(), area);
    }

    private Set<String> normalizeAreas(Set<String> areas) {
        return areas.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private TimeSeriesMatrix readMatrix(Path file, String horizon) throws IOException {
        String fileName = file.getFileName().toString().toLowerCase();
        TimeSeriesReader localReader = (this.timeSeriesReader != null) ? this.timeSeriesReader : new TimeSeriesReader();
        try {
            if (fileName.endsWith(".xlsx")) return localReader.readFromXlsx(file, horizon);
            if (fileName.endsWith(".txt") || fileName.endsWith(".csv")) return localReader.readFromTxt(file);
            return null;
        } catch (RuntimeException ex) {
            throw new IOException(ex);
        }
    }

    private Map<String, List<String>> buildFilesByArea(List<Path> generatedFiles) {
        Map<String, List<String>> filesByArea = new HashMap<>();
        for (Path path : generatedFiles) {
            String fileName = path.getFileName().toString();
            // Expected filename: AREA_GROUP.UUID.arrow or AREA.UUID.arrow
            int delimiterIndex = fileName.indexOf('_');
            if (delimiterIndex == -1) {
                delimiterIndex = fileName.indexOf('.');
            }
            String area = (delimiterIndex != -1) ? fileName.substring(0, delimiterIndex).toUpperCase() : fileName.toUpperCase();
            filesByArea.computeIfAbsent(area, ignored -> new ArrayList<>()).add(fileName);
        }
        return filesByArea;
    }
}