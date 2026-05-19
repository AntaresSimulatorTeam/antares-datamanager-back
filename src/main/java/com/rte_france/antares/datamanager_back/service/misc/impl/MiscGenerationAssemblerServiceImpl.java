package com.rte_france.antares.datamanager_back.service.misc.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.mapper.MiscGenMapper;
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


@Slf4j
@Service
public class MiscGenerationAssemblerServiceImpl  implements MiscGenerationAssemblerService {

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

        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.MISC_CAPACITY.name().equals(t.getType()))
                .map(TrajectoryEntity::getMiscClusterCapacityEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(c -> c.getCapacityByYear() != null && c.getCapacityByYear().compareTo(java.math.BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        misc -> misc.getArea().toUpperCase(),
                        Collectors.mapping(
                                misc -> {
                                    MiscGenerationDTO dto = MiscGenMapper.mapToMiscGenerationDTO(misc);
                                    dto.setMiscGenTsList(filesByArea.getOrDefault(misc.getArea().toUpperCase(), Collections.emptyList()));
                                    return dto;
                                },
                                Collectors.toList()
                        )
                ));
    }



    private List<Path> createSplitMiscGenFiles(StudyEntity study) {
        String horizon = study.getHorizon();
        String nasDir = antaresDataManagerProperties.getNasDirectory();
        String trajPath = antaresDataManagerProperties.getTrajectoryFilePath();
        String miscLoadDir = antaresDataManagerProperties.getMiscLoadDirectory();

        // 1. Group trajectories by type and by area (area)
        Map<String, TrajectoryEntity> miscCapacityByArea = mapTrajectoriesByArea(study, TrajectoryType.MISC_CAPACITY);
        Map<String, TrajectoryEntity> miscLoadByArea = mapTrajectoriesByArea(study, TrajectoryType.MISC_LOAD);

        List<Path> allGeneratedFiles = new ArrayList<>();
        Set<String> processedAreas = new HashSet<>();

        // 2. Specific areas treatment
        for (String area : nonOtherAreas(miscCapacityByArea.keySet())) {
            TrajectoryEntity capacityTraj = miscCapacityByArea.get(area);
            TrajectoryEntity loadTraj = resolveLoadTrajectoryForArea(area, miscLoadByArea);

            if (loadTraj != null && loadTraj.getFileName() != null) {
                allGeneratedFiles.addAll(processTrajectoryPair(study, horizon, nasDir, trajPath, miscLoadDir, area, capacityTraj, loadTraj.getFileName(), Set.of()));
                processedAreas.add(area);
            }
        }

        // 3.OTHERS case
        TrajectoryEntity capacityOthers = miscCapacityByArea.get(OTHER_AREA.toUpperCase());
        TrajectoryEntity loadOthers = miscLoadByArea.get(OTHER_AREA.toUpperCase());

        if (capacityOthers != null && loadOthers != null && loadOthers.getFileName() != null) {
            allGeneratedFiles.addAll(processTrajectoryPair(study, horizon, nasDir, trajPath, miscLoadDir, OTHER_AREA, capacityOthers, loadOthers.getFileName(), processedAreas));
        }

        return allGeneratedFiles;
    }

    private TrajectoryEntity resolveLoadTrajectoryForArea(String area, Map<String, TrajectoryEntity> miscLoadByArea) {
        TrajectoryEntity directLoadTrajectory = miscLoadByArea.get(area.toUpperCase());
        if (directLoadTrajectory != null) {
            return directLoadTrajectory;
        }

        if ("FR".equalsIgnoreCase(area)) {
            return null;
        }

        return miscLoadByArea.get(OTHER_AREA.toUpperCase());
    }

    private List<Path> processTrajectoryPair(StudyEntity study, String horizon, String nasDir, String trajectoryName, String miscLoadDir, String area, TrajectoryEntity capacityTraj, String loadFileName, Set<String> processedAreas) {
        Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> groupToAreas = resolveGroupToAreas(study, area, capacityTraj);

        if (loadFileName == null) {
            return Collections.emptyList();
        }

        if (groupToAreas.isEmpty()) {
            return Collections.emptyList();
        }

        List<Path> generatedFiles = new ArrayList<>();
        Map<String, double[]> weightedOtherSeriesByArea = new LinkedHashMap<>();
        Map<String, Double> totalOtherCapacityByArea = new LinkedHashMap<>();
        Path baseMiscLoadPath = Path.of(nasDir).resolve(trajectoryName).resolve(miscLoadDir).resolve(loadFileName);

        for (Map.Entry<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> entry : groupToAreas.entrySet()) {
            MiscFileProcessorServiceImpl.GroupClusterKey key = entry.getKey();
            String normalizedGroup = MiscGroupEnum.normalizeForGenerator(key.groupe());
            Set<String> areasToProcess = entry.getValue().stream()
                    .filter(a -> !processedAreas.contains(a.toUpperCase()))
                    .collect(Collectors.toSet());

            if (areasToProcess.isEmpty()) {
                continue;
            }

            Path tsFilePath = MiscFileProcessorServiceImpl.getLoadFactorByGroupPath(horizon, baseMiscLoadPath, key);

            if (Files.exists(tsFilePath)) {
                try {
                    if (MiscGroupEnum.OTHER.value().equals(normalizedGroup)) {
                        aggregateSeriesIntoTargetGroup(
                                tsFilePath,
                                key,
                                areasToProcess,
                                horizon,
                                capacityTraj,
                                weightedOtherSeriesByArea,
                                totalOtherCapacityByArea
                        );
                    } else {
                        generatedFiles.addAll(splitMiscGenLoadFiles(tsFilePath, areasToProcess, horizon, normalizedGroup));
                    }
                } catch (IOException e) {
                    log.error("Error splitting file {}", tsFilePath, e);
                }
            } else {
                log.warn("Load factor file not found for trajectory={} area={} group={} cluster={} expectedPath={}",
                        loadFileName,
                        area,
                        key.groupe(),
                        key.cluster(),
                        tsFilePath);
            }
        }

        try {
            generatedFiles.addAll(writeAggregatedOtherSeries(weightedOtherSeriesByArea, totalOtherCapacityByArea));
        } catch (IOException e) {
            log.error("Error writing aggregated misc other series for area {}", area, e);
        }
        return generatedFiles;
    }

    private void aggregateSeriesIntoTargetGroup(
            Path file,
            MiscFileProcessorServiceImpl.GroupClusterKey groupClusterKey,
            Set<String> areas,
            String horizon,
            TrajectoryEntity capacityTrajectory,
            Map<String, double[]> weightedSumByArea,
            Map<String, Double> totalCapacityByArea
    ) throws IOException {
        Map<String, double[]> areaSeries = readSeriesByArea(file, areas, horizon);
        for (Map.Entry<String, double[]> areaSeriesEntry : areaSeries.entrySet()) {
            String area = areaSeriesEntry.getKey();
            double[] values = areaSeriesEntry.getValue();
            double capacity = getCapacityForAreaGroupCluster(capacityTrajectory, area, groupClusterKey);

            if (capacity <= 0d) {
                continue;
            }

            double[] existing = weightedSumByArea.get(area);
            if (existing == null) {
                double[] weightedValues = new double[values.length];
                for (int i = 0; i < values.length; i++) {
                    weightedValues[i] = values[i] * capacity;
                }
                weightedSumByArea.put(area, weightedValues);
            } else {
                if (existing.length != values.length) {
                    throw TechnicalException.builder()
                            .message("Cannot aggregate MISC load factor series with different row counts for area " + area)
                            .build();
                }
                for (int i = 0; i < existing.length; i++) {
                    existing[i] += values[i] * capacity;
                }
            }

            totalCapacityByArea.merge(area, capacity, Double::sum);
        }
    }

    private List<Path> writeAggregatedOtherSeries(
            Map<String, double[]> weightedSumByArea,
            Map<String, Double> totalCapacityByArea
    ) throws IOException {
        if (weightedSumByArea.isEmpty()) {
            return Collections.emptyList();
        }

        String outputDir = antaresDataManagerProperties.getMiscGenTsOutputDirectory();
        List<Path> generatedFiles = new ArrayList<>();

        for (Map.Entry<String, double[]> entry : weightedSumByArea.entrySet()) {
            String area = entry.getKey();
            Double totalCapacity = totalCapacityByArea.get(area);
            if (totalCapacity == null || totalCapacity <= 0d) {
                continue;
            }

            double[] weightedSum = entry.getValue();
            double[] weightedAverage = new double[weightedSum.length];
            for (int i = 0; i < weightedSum.length; i++) {
                weightedAverage[i] = weightedSum[i] / totalCapacity;
            }

            TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn(area, weightedAverage)));
            String outputFileName = nasFileService.saveMatrixToNas(
                    matrix,
                    area.toUpperCase() + "_" + MiscGroupEnum.OTHER.value(),
                    outputDir
            );
            generatedFiles.add(Path.of(outputDir).resolve(outputFileName));
        }

        return generatedFiles;
    }

    private double getCapacityForAreaGroupCluster(
            TrajectoryEntity capacityTrajectory,
            String area,
            MiscFileProcessorServiceImpl.GroupClusterKey groupClusterKey
    ) {
        if (capacityTrajectory == null || capacityTrajectory.getMiscClusterCapacityEntities() == null) {
            return 0d;
        }

        return capacityTrajectory.getMiscClusterCapacityEntities().stream()
                .filter(Objects::nonNull)
                .filter(entity -> entity.getCapacityByYear() != null && entity.getCapacityByYear().doubleValue() > 0d)
                .filter(entity -> entity.getArea() != null && entity.getArea().equalsIgnoreCase(area))
                .filter(entity -> entity.getGroupe() != null && entity.getGroupe().equalsIgnoreCase(groupClusterKey.groupe()))
                .filter(entity -> isSameCluster(entity.getCluster(), groupClusterKey.cluster()))
                .mapToDouble(entity -> entity.getCapacityByYear().doubleValue())
                .sum();
    }

    private boolean isSameCluster(String entityCluster, String expectedCluster) {
        if (expectedCluster == null || expectedCluster.isBlank()) {
            return true;
        }
        return entityCluster != null && entityCluster.equalsIgnoreCase(expectedCluster);
    }

    private Map<String, double[]> readSeriesByArea(Path file, Set<String> areas, String horizon) throws IOException {
        Set<String> allowedAreas = normalizeAreas(areas);
        TimeSeriesMatrix matrix = readMatrix(file, horizon);
        if (matrix == null) {
            return Collections.emptyMap();
        }

        Map<String, double[]> seriesByArea = new LinkedHashMap<>();
        for (var column : matrix.columns()) {
            String colName = column.name() != null ? column.name().trim() : "";
            if (!colName.isEmpty() && allowedAreas.contains(colName.toLowerCase())) {
                seriesByArea.put(colName.toUpperCase(), Arrays.copyOf(column.values(), column.values().length));
            }
        }

        return seriesByArea;
    }




    public List<Path> splitMiscGenLoadFiles(Path file, Set<String> areas, String horizon, String groupName) throws IOException {
        Set<String> allowedAreas = normalizeAreas(areas);

        List<Path> generatedFiles = new ArrayList<>();
        TimeSeriesMatrix matrix = readMatrix(file, horizon);
        if (matrix == null) {
            return generatedFiles;
        }

        if (matrix.columns().isEmpty()) {
            return generatedFiles;
        }

        String outputDir = antaresDataManagerProperties.getMiscGenTsOutputDirectory();

        for (var column : matrix.columns()) {
            String colName = column.name() != null ? column.name().trim() : "";
            if (colName.isEmpty()) continue;

            if (allowedAreas.contains(colName.toLowerCase())) {
                TimeSeriesMatrix singleColMatrix = new TimeSeriesMatrix(List.of(column));

                String baseName = groupName != null && !groupName.isEmpty() ? colName.toUpperCase() + "_" + groupName : colName.toUpperCase();
                String outputFileName = nasFileService.saveMatrixToNas(singleColMatrix, baseName, outputDir);
                generatedFiles.add(Path.of(outputDir). resolve(outputFileName));
            }
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

    private Set<String> nonOtherAreas(Set<String> areas) {
        Set<String> result = new HashSet<>(areas);
        result.remove(OTHER_AREA.toUpperCase());
        return result;
    }

    private Map<MiscFileProcessorServiceImpl.GroupClusterKey, List<String>> resolveGroupToAreas(
            StudyEntity study,
            String area,
            TrajectoryEntity capacityTrajectory
    ) {
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
            if (fileName.endsWith(".xlsx")) {
                return localReader.readFromXlsx(file, horizon);
            }
            if (fileName.endsWith(".txt") || fileName.endsWith(".csv")) {
                return localReader.readFromTxt(file);
            }
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
            String area = fileName.split("[_.]")[0].toUpperCase();
            filesByArea.computeIfAbsent(area, ignored -> new ArrayList<>()).add(fileName);
        }
        return filesByArea;
    }


}
