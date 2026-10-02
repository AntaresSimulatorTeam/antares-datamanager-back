package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.HydroAllocationMeRepository;
import com.rte_france.antares.datamanager_back.repository.HydroCapacityMeRepository;
import com.rte_france.antares.datamanager_back.repository.HydroParametersMeRepository;
import com.rte_france.antares.datamanager_back.repository.model.HydroAllocationMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroCapacityMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroParametersMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class HydroMeToJsonService {

    private static final String PROPERTIES = "properties";
    private static final String RESERVOIR_CAPACITY = "reservoir_capacity";
    private static final String RESERVOIR = "reservoir";
    private static final String INTER_DAILY_BREAKDOWN = "inter_daily_breakdown";
    private static final String INTRA_DAILY_MODULATION = "intra_daily_modulation";
    private static final String INTER_MONTHLY_BREAKDOWN = "inter_monthly_breakdown";
    private static final String INITIALIZE_RESERVOIR_DATE = "initialize_reservoir_date";
    private static final String LEEWAY_LOW = "leeway_low";
    private static final String LEEWAY_UP = "leeway_up";
    private static final String PUMPING_EFFICIENCY = "pumping_efficiency";
    private static final String USE_WATER = "use_water";
    private static final String USE_HEURISTIC = "use_heuristic";
    private static final String HARD_BOUNDS = "hard_bounds";
    private static final String POWER_TO_LEVEL = "power_to_level";
    private static final String USE_LEEWAY = "use_leeway";
    private static final String FOLLOW_LOAD = "follow_load";
    private static final String INFLOW_STRUCTURE = "inflow_structure";
    private static final String INTERMONTHLY_CORRELATION = "intermonthly_correlation";
    private static final String GENERATING_PMAX = "generating_pmax";
    private static final String PUMPING_PMAX = "pumping_pmax";
    private static final String GENERATING_SERIES = "generating_series";
    private static final String PUMPING_SERIES = "pumping_series";
    private static final String RESERVOIR_TS = "reservoir_ts";
    private static final String TIMESERIES_TS = "timeseries_ts";
    private static final String WATER_VALUES_TS = "water_values_ts";
    private static final String ROR = "ror";
    private static final String MOD = "mod";
    private static final String ALLOCATION = "allocation";
    private static final String PMAX = "pmax";
    private static final String HOURS = "hours";
    private static final String ANNUAL = "annual";
    private static final String HOURLY = "hourly";
    private static final String DAILY = "daily";
    private static final String GENERATING_PREFIX = "generating";
    private static final String PUMPING_PREFIX = "pumping";
    private static final String GENERATING_PMAX_DAILY_TS_DIR = "Generating Pmax daily ts";
    private static final String PUMPING_PMAX_DAILY_TS_DIR = "Pumping Pmax daily ts";
    private static final String RESERVOIR_LEVELS_FILE_SUFFIX = "_reservoir_levels.xlsx";
    private static final String EXCEL_EXTENSION = ".xlsx";
    private static final List<String> RESERVOIR_LEVELS_HEADERS =
            List.of("date", "minimum", "moyenne", "maximum");
    private static final int DAYS_PER_YEAR = 365;

    private final HydroCapacityMeRepository hydroCapacityMeRepository;
    private final HydroParametersMeRepository hydroParametersMeRepository;
    private final HydroAllocationMeRepository hydroAllocationMeRepository;
    private final NasFileService nasFileService;
    private final TimeSeriesReader timeSeriesReader;
    private final AntaresDataManagerProperties antaresDataManagerProperties;

    public Map<String, Object> buildHydroMeMap(TrajectoryEntity hydroCapacityMeTrajectory) {
        return buildHydroMeMap(hydroCapacityMeTrajectory, null);
    }

    /**
     * Builds the "hydro_me" map keyed by node, merging HYDRO_CAPACITY_ME data (reservoir capacity, pmax / series)
     * with HYDRO_PARAMETERS_ME data (hydro parameters, inflow structure and allocation).
     * For each timestep (generating / pumping): "annual" produces a {pmax, hours} entry,
     * "daily" reads the node column (365 daily values) from the associated "Generating/Pumping Pmax daily ts"
     * trajectory file, saves it as "generating_<node>.<uuid>.arrow" / "pumping_<node>.<uuid>.arrow" in the hydro ME output
     * directory and puts the arrow file name in the series entry.
     */
    public Map<String, Object> buildHydroMeMap(TrajectoryEntity hydroCapacityMeTrajectory,
                                               TrajectoryEntity hydroParametersMeTrajectory) {
        return buildHydroMeMap(hydroCapacityMeTrajectory, hydroParametersMeTrajectory, null, null);
    }

    public Map<String, Object> buildHydroMeMap(TrajectoryEntity hydroCapacityMeTrajectory,
                                               TrajectoryEntity hydroParametersMeTrajectory,
                                               TrajectoryEntity hydroReservoirLevelsMeTrajectory) {
        return buildHydroMeMap(
                hydroCapacityMeTrajectory, hydroParametersMeTrajectory, hydroReservoirLevelsMeTrajectory, null);
    }

    public Map<String, Object> buildHydroMeMap(TrajectoryEntity hydroCapacityMeTrajectory,
                                               TrajectoryEntity hydroParametersMeTrajectory,
                                               TrajectoryEntity hydroReservoirLevelsMeTrajectory,
                                               TrajectoryEntity hydroTimeSeriesMeTrajectory) {
        return buildHydroMeMap(hydroCapacityMeTrajectory, hydroParametersMeTrajectory,
                hydroReservoirLevelsMeTrajectory, hydroTimeSeriesMeTrajectory, null);
    }

    public Map<String, Object> buildHydroMeMap(TrajectoryEntity hydroCapacityMeTrajectory,
                                               TrajectoryEntity hydroParametersMeTrajectory,
                                               TrajectoryEntity hydroReservoirLevelsMeTrajectory,
                                               TrajectoryEntity hydroTimeSeriesMeTrajectory,
                                               TrajectoryEntity hydroWaterValuesMeTrajectory) {
        Map<String, HydroCapacityMeEntity> capacityByNode = loadCapacities(hydroCapacityMeTrajectory);
        Map<String, HydroParametersMeEntity> parametersByNode = loadParameters(hydroParametersMeTrajectory);
        Map<String, Map<String, BigDecimal>> allocationByNode = loadAllocations(hydroParametersMeTrajectory);

        Map<String, String> nodeNames = new LinkedHashMap<>();
        capacityByNode.values().forEach(c -> nodeNames.putIfAbsent(normalize(c.getNode()), c.getNode()));
        parametersByNode.values().forEach(p -> nodeNames.putIfAbsent(normalize(p.getNode()), p.getNode()));

        if (nodeNames.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> generatingSeries = loadDailySeries(
                hydroCapacityMeTrajectory, capacityByNode, nodeNames, PmaxKind.GENERATING);
        Map<String, String> pumpingSeries = loadDailySeries(
                hydroCapacityMeTrajectory, capacityByNode, nodeNames, PmaxKind.PUMPING);
        Map<String, String> reservoirSeries = buildReservoirTs(hydroReservoirLevelsMeTrajectory);
        Map<String, Map<String, String>> timeSeries = buildTimeSeriesTs(hydroTimeSeriesMeTrajectory, nodeNames);
        Map<String, String> waterValues = buildWaterValuesTs(hydroWaterValuesMeTrajectory, nodeNames);

        Map<String, Object> hydroMeMap = new LinkedHashMap<>();

        nodeNames.forEach((key, nodeName) -> {
            HydroCapacityMeEntity capacity = capacityByNode.get(key);
            HydroParametersMeEntity parameters = parametersByNode.get(key);

            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put(PROPERTIES, buildProperties(capacity, parameters));

            if (parameters != null) {
                Map<String, Object> inflowStructure = new LinkedHashMap<>();
                inflowStructure.put(INTERMONTHLY_CORRELATION, parameters.getInterMonthlyCorrelation());
                nodeMap.put(INFLOW_STRUCTURE, inflowStructure);
            }
            String reservoirSeriesFile = reservoirSeries.get(key);
            if (reservoirSeriesFile != null) {
                nodeMap.put(RESERVOIR_TS, reservoirSeriesFile);
            }
            Map<String, String> nodeTimeSeries = timeSeries.get(key);
            if (nodeTimeSeries != null && !nodeTimeSeries.isEmpty()) {
                nodeMap.put(TIMESERIES_TS, nodeTimeSeries);
            }
            String waterValuesFile = waterValues.get(key);
            if (waterValuesFile != null) {
                nodeMap.put(WATER_VALUES_TS, waterValuesFile);
            }

            if (capacity != null) {
                addPmaxEntry(nodeMap, nodeName, capacity.getGeneratingPmaxTimestep(), PmaxKind.GENERATING,
                        capacity.getGeneratingPmax(), capacity.getHoursAtGeneratingPmax(), generatingSeries);
                addPmaxEntry(nodeMap, nodeName, capacity.getPumpingPmaxTimestep(), PmaxKind.PUMPING,
                        capacity.getPumpingPmax(), capacity.getHoursAtPumpingPmax(), pumpingSeries);
            }

            Map<String, BigDecimal> allocation = allocationByNode.get(key);
            if (allocation != null && !allocation.isEmpty()) {
                nodeMap.put(ALLOCATION, allocation);
            }

            hydroMeMap.put(nodeName, nodeMap);
        });

        log.info("hydroMeMapGenerator: {} nodes added", hydroMeMap.size());
        return hydroMeMap;
    }

    public Map<String, String> buildReservoirTs(TrajectoryEntity trajectory) {
        if (trajectory == null) {
            return Collections.emptyMap();
        }

        Path baseDirectory = Path.of(antaresDataManagerProperties.getNasDirectory(),
                antaresDataManagerProperties.getTrajectoryFilePath(),
                antaresDataManagerProperties.getHydroReservoirLevelsMeDirectory())
                .toAbsolutePath().normalize();
        Path trajectoryDirectory = baseDirectory.resolve(trajectory.getFileName()).normalize();
        if (!trajectoryDirectory.startsWith(baseDirectory)) {
            throw BusinessException.builder()
                    .message("Invalid HYDRO_ME Reservoir Levels trajectory directory {0}")
                    .errorMessageArguments(List.of(trajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (!Files.isDirectory(trajectoryDirectory)) {
            throw BusinessException.builder()
                    .message("Missing HYDRO_ME Reservoir Levels trajectory directory {0}")
                    .errorMessageArguments(List.of(trajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (StringUtils.isBlank(trajectory.getChecksum())) {
            throw TechnicalException.builder()
                    .message("Missing checksum for HYDRO_ME Reservoir Levels trajectory " + trajectory.getFileName())
                    .build();
        }

        try (var files = Files.list(trajectoryDirectory)) {
            List<Path> reservoirFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .endsWith(RESERVOIR_LEVELS_FILE_SUFFIX))
                    .sorted()
                    .toList();
            if (reservoirFiles.isEmpty()) {
                throw BusinessException.builder()
                        .message("No reservoir levels files found in HYDRO_ME trajectory {0}")
                        .errorMessageArguments(List.of(trajectory.getFileName()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            Map<String, String> reservoirSeries = new LinkedHashMap<>();
            for (Path file : reservoirFiles) {
                TimeSeriesMatrix matrix = timeSeriesReader.readFromXlsx(
                        file, resolveHorizonSheet(trajectory.getHorizon()), true);
                validateReservoirLevelsHeaders(matrix, file);

                TimeSeriesMatrix withoutDate = new TimeSeriesMatrix(matrix.columns().subList(1, 4));
                String sourceName = file.getFileName().toString();
                String baseName = sourceName.substring(0, sourceName.length() - EXCEL_EXTENSION.length());
                String nodeName = baseName.substring(
                        0, baseName.length() - "_reservoir_levels".length());
                String arrowFileName = baseName + "_" + trajectory.getChecksum() + ".arrow";
                nasFileService.saveMatrixToNasWithName(
                        withoutDate, arrowFileName, antaresDataManagerProperties.getHydroMeOutputDirectory());
                reservoirSeries.put(normalize(nodeName), arrowFileName);
                log.info("Hydro ME reservoir series for {} saved to {}", baseName, arrowFileName);
            }
            return reservoirSeries;
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Failed to generate HYDRO_ME Reservoir Levels series from trajectory "
                            + trajectory.getFileName())
                    .cause(e)
                    .build();
        }
    }

    private Map<String, Map<String, String>> buildTimeSeriesTs(
            TrajectoryEntity trajectory, Map<String, String> nodeNames) {
        if (trajectory == null) {
            return Collections.emptyMap();
        }
        if (StringUtils.isBlank(trajectory.getChecksum())) {
            throw TechnicalException.builder()
                    .message("Missing checksum for HYDRO_TIME_SERIES_ME trajectory " + trajectory.getFileName())
                    .build();
        }

        Path baseDirectory = Path.of(antaresDataManagerProperties.getNasDirectory(),
                antaresDataManagerProperties.getTrajectoryFilePath(),
                antaresDataManagerProperties.getHydroTimeSeriesMeDirectory())
                .toAbsolutePath().normalize();
        Path trajectoryDirectory = baseDirectory.resolve(trajectory.getFileName()).normalize();
        if (!trajectoryDirectory.startsWith(baseDirectory)) {
            throw BusinessException.builder()
                    .message("Invalid HYDRO_TIME_SERIES_ME trajectory directory {0}")
                    .errorMessageArguments(List.of(trajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (!Files.isDirectory(trajectoryDirectory)) {
            throw BusinessException.builder()
                    .message("Missing HYDRO_TIME_SERIES_ME trajectory directory {0}")
                    .errorMessageArguments(List.of(trajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        try (var directories = Files.list(trajectoryDirectory)) {
            List<Path> nodeDirectories = directories
                    .filter(Files::isDirectory)
                    .filter(directory -> nodeNames.containsKey(normalize(directory.getFileName().toString())))
                    .sorted()
                    .toList();
            if (nodeDirectories.isEmpty()) {
                throw BusinessException.builder()
                        .message("No matching node directories found in HYDRO_TIME_SERIES_ME trajectory {0}")
                        .errorMessageArguments(List.of(trajectory.getFileName()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            Map<String, Map<String, String>> result = new LinkedHashMap<>();
            for (Path nodeDirectory : nodeDirectories) {
                String node = nodeDirectory.getFileName().toString();
                Map<String, String> nodeSeries = new LinkedHashMap<>();
                nodeSeries.put(ROR, readAndSaveHydroTimeSeries(
                        nodeDirectory.resolve("ror.xlsx"), node, ROR, trajectory));
                nodeSeries.put(MOD, readAndSaveHydroTimeSeries(
                        nodeDirectory.resolve("mod.xlsx"), node, MOD, trajectory));
                result.put(normalize(node), nodeSeries);
            }
            return result;
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Failed to generate HYDRO_TIME_SERIES_ME Arrow files from trajectory "
                            + trajectory.getFileName())
                    .cause(e)
                    .build();
        }
    }

    private String readAndSaveHydroTimeSeries(
            Path xlsxFile, String node, String seriesName, TrajectoryEntity trajectory) throws IOException {
        if (!Files.isRegularFile(xlsxFile)) {
            throw BusinessException.builder()
                    .message("Missing {0}.xlsx in node directory {1} of HYDRO_TIME_SERIES_ME trajectory {2}")
                    .errorMessageArguments(List.of(seriesName, node, trajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        TimeSeriesMatrix source;
        try {
            source = timeSeriesReader.readFromXlsx(
                    xlsxFile, resolveHorizonSheet(trajectory.getHorizon()), false);
        } catch (BusinessException e) {
            String originalMessage = MessageFormat.format(
                    e.getMessage(), e.getErrorMessageArguments().toArray());
            throw BusinessException.builder()
                    .message("Failed to read {0}.xlsx for node {1}: {2}")
                    .errorMessageArguments(List.of(seriesName, node, originalMessage))
                    .httpStatus(e.getHttpStatus())
                    .antaresErrorCode(e.getAntaresErrorCode())
                    .build();
        }
        if (source.columns().isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing column A in {0}.xlsx for node {1} of HYDRO_TIME_SERIES_ME trajectory {2}")
                    .errorMessageArguments(List.of(seriesName, node, trajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        TimeSeriesMatrix series = new TimeSeriesMatrix(List.of(
                new TimeSeriesMatrixColumn(seriesName, source.columns().get(0).values())));
        String arrowFileName = node + "_" + seriesName + "." + trajectory.getChecksum() + ".arrow";
        nasFileService.saveMatrixToNasWithName(
                series, arrowFileName, antaresDataManagerProperties.getHydroMeOutputDirectory());
        log.info("Hydro ME {} series for node {} saved to {}", seriesName, node, arrowFileName);
        return arrowFileName;
    }

    private Map<String, String> buildWaterValuesTs(
            TrajectoryEntity trajectory, Map<String, String> nodeNames) {
        if (trajectory == null) {
            return Collections.emptyMap();
        }
        if (StringUtils.isBlank(trajectory.getChecksum())) {
            throw TechnicalException.builder()
                    .message("Missing checksum for HYDRO_WATER_VALUES_ME trajectory " + trajectory.getFileName())
                    .build();
        }

        Path baseDirectory = Path.of(antaresDataManagerProperties.getNasDirectory(),
                antaresDataManagerProperties.getTrajectoryFilePath(),
                antaresDataManagerProperties.getHydroWaterValuesMeDirectory())
                .toAbsolutePath().normalize();
        Path trajectoryDirectory = baseDirectory.resolve(trajectory.getFileName()).normalize();
        if (!trajectoryDirectory.startsWith(baseDirectory)) {
            throw BusinessException.builder()
                    .message("Invalid HYDRO_WATER_VALUES_ME trajectory directory {0}")
                    .errorMessageArguments(List.of(trajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (!Files.isDirectory(trajectoryDirectory)) {
            throw BusinessException.builder()
                    .message("Missing HYDRO_WATER_VALUES_ME trajectory directory {0}")
                    .errorMessageArguments(List.of(trajectory.getFileName()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        try (var files = Files.list(trajectoryDirectory)) {
            List<Path> xlsxFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXCEL_EXTENSION))
                    .sorted()
                    .toList();
            Map<String, String> valuesByNode = new LinkedHashMap<>();
            for (Path xlsxFile : xlsxFiles) {
                String fileName = xlsxFile.getFileName().toString().toLowerCase(Locale.ROOT);
                List<Map.Entry<String, String>> matchingNodes = nodeNames.entrySet().stream()
                        .filter(entry -> fileName.contains(entry.getValue().toLowerCase(Locale.ROOT)))
                        .toList();
                if (matchingNodes.size() > 1) {
                    throw BusinessException.builder()
                            .message("Water Values file {0} matches multiple nodes: {1}")
                            .errorMessageArguments(List.of(
                                    xlsxFile.getFileName().toString(),
                                    matchingNodes.stream().map(Map.Entry::getValue).collect(java.util.stream.Collectors.joining(", "))))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                if (matchingNodes.isEmpty()) {
                    continue;
                }

                Map.Entry<String, String> nodeEntry = matchingNodes.get(0);
                TimeSeriesMatrix matrix;
                try {
                    matrix = timeSeriesReader.readFromXlsx(
                            xlsxFile, resolveHorizonSheet(trajectory.getHorizon()), false);
                } catch (BusinessException e) {
                    String originalMessage = MessageFormat.format(
                            e.getMessage(), e.getErrorMessageArguments().toArray());
                    throw BusinessException.builder()
                            .message("Failed to read Water Values file for node {0}: {1}")
                            .errorMessageArguments(List.of(nodeEntry.getValue(), originalMessage))
                            .httpStatus(e.getHttpStatus())
                            .antaresErrorCode(e.getAntaresErrorCode())
                            .build();
                }
                if (matrix.columns().isEmpty()) {
                    throw BusinessException.builder()
                            .message("Water Values file {0} for node {1} has no data in column A")
                            .errorMessageArguments(List.of(xlsxFile.getFileName().toString(), nodeEntry.getValue()))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }

                String arrowFileName = nodeEntry.getValue() + "_" + trajectory.getChecksum() + ".arrow";
                nasFileService.saveMatrixToNasWithName(
                        matrix, arrowFileName, antaresDataManagerProperties.getHydroMeOutputDirectory());
                valuesByNode.put(nodeEntry.getKey(), arrowFileName);
                log.info("Hydro ME Water Values series for node {} saved to {}", nodeEntry.getValue(), arrowFileName);
            }
            if (valuesByNode.isEmpty()) {
                throw BusinessException.builder()
                        .message("No Water Values files matched HYDRO_ME nodes in trajectory {0}")
                        .errorMessageArguments(List.of(trajectory.getFileName()))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
            return valuesByNode;
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Failed to generate HYDRO_WATER_VALUES_ME Arrow files from trajectory "
                            + trajectory.getFileName())
                    .cause(e)
                    .build();
        }
    }

    private static void validateReservoirLevelsHeaders(TimeSeriesMatrix matrix, Path file) {
        List<String> headers = matrix.columns().stream()
                .map(TimeSeriesMatrixColumn::name)
                .map(HydroMeToJsonService::normalize)
                .toList();
        if (!headers.equals(RESERVOIR_LEVELS_HEADERS)) {
            throw BusinessException.builder()
                    .message("Invalid headers in HYDRO_ME Reservoir Levels file {0}; expected Date, Minimum, Moyenne, Maximum in this order")
                    .errorMessageArguments(List.of(file.getFileName().toString()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private Map<String, Object> buildProperties(HydroCapacityMeEntity capacity, HydroParametersMeEntity parameters) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (capacity != null) {
            properties.put(RESERVOIR_CAPACITY, capacity.getReservoirCapacity());
        }
        if (parameters != null) {
            properties.put(RESERVOIR, parameters.getReservoirManagement());
            properties.put(INTER_DAILY_BREAKDOWN, parameters.getInterDailyBreakdown());
            properties.put(INTRA_DAILY_MODULATION, parameters.getIntraDailyModulation());
            properties.put(INTER_MONTHLY_BREAKDOWN, parameters.getInterMonthlyBreakdown());
            properties.put(INITIALIZE_RESERVOIR_DATE, parameters.getInitializeReservoirDate());
            properties.put(LEEWAY_LOW, parameters.getLeewayLow());
            properties.put(LEEWAY_UP, parameters.getLeewayUp());
            properties.put(PUMPING_EFFICIENCY, parameters.getPumpingEfficiency());
            properties.put(USE_WATER, parameters.getUseWater());
            properties.put(USE_HEURISTIC, parameters.getUseHeuristic());
            properties.put(HARD_BOUNDS, parameters.getHardBounds());
            properties.put(POWER_TO_LEVEL, parameters.getPowerToLevel());
            properties.put(USE_LEEWAY, parameters.getUseLeeway());
            properties.put(FOLLOW_LOAD, parameters.getFollowLoad());
        }
        return properties;
    }

    private Map<String, HydroCapacityMeEntity> loadCapacities(TrajectoryEntity trajectory) {
        if (trajectory == null || trajectory.getId() == null) {
            return Collections.emptyMap();
        }
        List<HydroCapacityMeEntity> capacities = hydroCapacityMeRepository.findByTrajectoryIdOrderByNodeAsc(trajectory.getId());
        Map<String, HydroCapacityMeEntity> byNode = new LinkedHashMap<>();
        if (capacities != null) {
            capacities.stream()
                    .filter(c -> c != null && StringUtils.isNotBlank(c.getNode()))
                    .forEach(c -> byNode.putIfAbsent(normalize(c.getNode()), c));
        }
        return byNode;
    }

    private Map<String, HydroParametersMeEntity> loadParameters(TrajectoryEntity trajectory) {
        if (trajectory == null || trajectory.getId() == null) {
            return Collections.emptyMap();
        }
        List<HydroParametersMeEntity> parameters = hydroParametersMeRepository.findByTrajectoryId(trajectory.getId());
        Map<String, HydroParametersMeEntity> byNode = new LinkedHashMap<>();
        if (parameters != null) {
            parameters.stream()
                    .filter(p -> p != null && StringUtils.isNotBlank(p.getNode()))
                    .forEach(p -> byNode.putIfAbsent(normalize(p.getNode()), p));
        }
        return byNode;
    }

    private Map<String, Map<String, BigDecimal>> loadAllocations(TrajectoryEntity trajectory) {
        if (trajectory == null || trajectory.getId() == null) {
            return Collections.emptyMap();
        }
        List<HydroAllocationMeEntity> allocations = hydroAllocationMeRepository.findByTrajectoryId(trajectory.getId());
        Map<String, Map<String, BigDecimal>> byNode = new LinkedHashMap<>();
        if (allocations != null) {
            allocations.stream()
                    .filter(a -> a != null && StringUtils.isNotBlank(a.getNode()) && StringUtils.isNotBlank(a.getArea()))
                    .forEach(a -> byNode.computeIfAbsent(normalize(a.getNode()), k -> new LinkedHashMap<>())
                            .put(a.getArea().trim(), a.getAllocationCoefficient()));
        }
        return byNode;
    }

    private enum PmaxKind {
        GENERATING(GENERATING_PMAX, GENERATING_SERIES, GENERATING_PREFIX, GENERATING_PMAX_DAILY_TS_DIR),
        PUMPING(PUMPING_PMAX, PUMPING_SERIES, PUMPING_PREFIX, PUMPING_PMAX_DAILY_TS_DIR);

        private final String pmaxKey;
        private final String seriesKey;
        private final String filePrefix;
        private final String tsDirectory;

        PmaxKind(String pmaxKey, String seriesKey, String filePrefix, String tsDirectory) {
            this.pmaxKey = pmaxKey;
            this.seriesKey = seriesKey;
            this.filePrefix = filePrefix;
            this.tsDirectory = tsDirectory;
        }
    }

    private void addPmaxEntry(Map<String, Object> nodeMap, String nodeName, String timestep, PmaxKind kind,
                              BigDecimal pmax, BigDecimal hours, Map<String, String> dailySeries) {
        String normalizedTimestep = StringUtils.trim(timestep);
        if (ANNUAL.equalsIgnoreCase(normalizedTimestep) || HOURLY.equalsIgnoreCase(normalizedTimestep)) {
            Map<String, Object> pmaxMap = new LinkedHashMap<>();
            pmaxMap.put(PMAX, pmax);
            pmaxMap.put(HOURS, hours);
            nodeMap.put(kind.pmaxKey, pmaxMap);
        } else if (DAILY.equalsIgnoreCase(normalizedTimestep)) {
            String arrowFileName = dailySeries.get(normalize(nodeName));
            if (arrowFileName != null) {
                nodeMap.put(kind.seriesKey, arrowFileName);
            }
        }
    }

    /**
     * Reads all requested daily node columns in one pass, then saves one Arrow file per node.
     */
    private Map<String, String> loadDailySeries(TrajectoryEntity trajectory,
                                                 Map<String, HydroCapacityMeEntity> capacityByNode,
                                                 Map<String, String> nodeNames,
                                                 PmaxKind kind) {
        if (trajectory == null) {
            return Collections.emptyMap();
        }

        Map<String, String> nodesToRead = new LinkedHashMap<>();
        capacityByNode.forEach((key, capacity) -> {
            String timestep = kind == PmaxKind.GENERATING
                    ? capacity.getGeneratingPmaxTimestep()
                    : capacity.getPumpingPmaxTimestep();
            if (DAILY.equalsIgnoreCase(StringUtils.trim(timestep))) {
                nodesToRead.put(key, nodeNames.get(key));
            }
        });
        if (nodesToRead.isEmpty()) {
            return Collections.emptyMap();
        }

        Path tsFile = Path.of(antaresDataManagerProperties.getNasDirectory(),
                        antaresDataManagerProperties.getTrajectoryFilePath(),
                        antaresDataManagerProperties.getHydroCapacityMeDirectory(),
                        kind.tsDirectory,
                        trajectory.getFileName() + EXCEL_EXTENSION)
                .normalize();
        if (!Files.isRegularFile(tsFile)) {
            String nodeName = nodesToRead.values().iterator().next();
            throw BusinessException.builder()
                    .message("Missing {0} file {1} in HYDRO_ME Capacity trajectory {2} (required for node {3} with daily timestep)")
                    .errorMessageArguments(List.of(kind.tsDirectory, trajectory.getFileName() + EXCEL_EXTENSION,
                            trajectory.getFileName(), nodeName.trim()))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        try {
            TimeSeriesMatrix matrix = timeSeriesReader.readSelectedColumnsFromXlsx(
                    tsFile, resolveHorizonSheet(trajectory.getHorizon()), Set.copyOf(nodesToRead.values()));
            Map<String, TimeSeriesMatrixColumn> columnsByNode = new LinkedHashMap<>();
            matrix.columns().stream()
                    .filter(c -> c.name() != null)
                    .forEach(c -> columnsByNode.putIfAbsent(normalize(c.name()), c));

            Map<String, String> arrowFilesByNode = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : nodesToRead.entrySet()) {
                String node = entry.getValue().trim();
                TimeSeriesMatrixColumn column = columnsByNode.get(normalize(node));
                if (column == null) {
                    throw BusinessException.builder()
                            .message("Missing node {0} in headers of {1} file {2} in HYDRO_ME Capacity trajectory {3} (required with daily timestep)")
                            .errorMessageArguments(List.of(node, kind.tsDirectory, trajectory.getFileName() + EXCEL_EXTENSION,
                                    trajectory.getFileName()))
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
                double[] values = column.values();
                double[] dailyValues = Arrays.copyOf(values, Math.min(values.length, DAYS_PER_YEAR));
                TimeSeriesMatrix nodeMatrix = new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn(node, dailyValues)));

                String arrowFileName = nasFileService.saveMatrixToNas(nodeMatrix, kind.filePrefix + "_" + node,
                        antaresDataManagerProperties.getHydroMeOutputDirectory());
                log.info("Hydro ME {} series for node {} saved to {}", kind.filePrefix, node, arrowFileName);
                arrowFilesByNode.put(entry.getKey(), arrowFileName);
            }
            return arrowFilesByNode;
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Failed to generate " + kind.filePrefix + " series arrows from file " + tsFile)
                    .cause(e)
                    .build();
        }
    }

    private static String resolveHorizonSheet(String horizon) {
        if (StringUtils.isBlank(horizon)) {
            return null;
        }
        String[] parts = horizon.split("-");
        return parts[parts.length - 1].trim();
    }

    private static String normalize(String node) {
        return node.trim().toLowerCase(Locale.ROOT);
    }
}
