package com.rte_france.antares.datamanager_back.service.nuclear.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.NuclearSMRMixageDTO;
import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.ClusterDesignationRepository;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearAvailabilityAssemblerService;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearAvailabilityAssemblyResult;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearClusterNames;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearFilePrefixes;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService.AreaClusterRefKey;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.NuclearTimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NuclearAvailabilityAssemblerServiceImpl implements NuclearAvailabilityAssemblerService {

    private static final String FR_AREA = "fr";
    private static final String LT_SIMU_FILE_PREFIX = "Simu_";
    private static final String XLSX_SUFFIX = ".xlsx";
    private static final String SEED_SUFFIX = "seed-tsgen-thermal";
    private static final String CP0_CP1_CP2_DESIGNATION = "cp0_cp1_cp2";
    private static final String N4_DESIGNATION = "n4";
    private static final String P4_DESIGNATION = "p4";
    private static final int HOURS_PER_DAY = 24;
    private static final int MAX_ROWS_PER_YEAR = 365;

    /**
     * Explicit, not name-derived: which referential designation backs which LT cluster names.
     */
    private static final List<LtDesignationGroup> LT_DESIGNATION_GROUPS = List.of(
            new LtDesignationGroup(N4_DESIGNATION, NuclearClusterNames::isN4),
            new LtDesignationGroup(P4_DESIGNATION, NuclearClusterNames::isP4),
            new LtDesignationGroup(CP0_CP1_CP2_DESIGNATION, NuclearAvailabilityAssemblerServiceImpl::isDefaultLongTermCluster)
    );

    private record LtDesignationGroup(String designation, Predicate<String> clusterNameMatcher) {
    }

    private final NasFileService nasFileService;
    private final TimeSeriesReader timeSeriesReader;
    private final NuclearTimeSeriesReader nuclearTimeSeriesReader;
    private final AntaresDataManagerProperties properties;
    private final PathSecurityUtil pathSecurityUtil;
    private final ClusterDesignationRepository clusterDesignationRepository;

    @Override
    public NuclearAvailabilityAssemblyResult assembleAvailability(StudyEntity study,
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps) {

        Set<TrajectoryEntity> trajectories = study.getTrajectories();
        Optional<TrajectoryEntity> eprTrajectory = findFirstByType(trajectories, TrajectoryType.NUCLEAR_FR_TS_ERP);
        Optional<TrajectoryEntity> ltTrajectory = findFirstByType(trajectories, TrajectoryType.NUCLEAR_FR_TS_LONG_TERM);
        Optional<TrajectoryEntity> smrTrajectory = findFirstByType(trajectories, TrajectoryType.NUCLEAR_FR_TS_SMR);

        String horizonYear = extractHorizonYear(study.getHorizon());
        Map<AreaClusterRefKey, String> seriesByCluster = new LinkedHashMap<>();
        Map<AreaClusterRefKey, NuclearSMRMixageDTO> smrMixageByCluster = new LinkedHashMap<>();

        ltTrajectory.ifPresent(traj -> LT_DESIGNATION_GROUPS.forEach(group -> {
            List<AreaClusterRefKey> matched = collectMatchingClusters(thermalClusterProps, group.clusterNameMatcher());
            if (!matched.isEmpty()) {
                String arrowFile = assembleLongTermSeries(traj, study.getHorizon(), group.designation());
                matched.forEach(key -> seriesByCluster.put(key, arrowFile));
            }
        }));

        eprTrajectory.ifPresent(traj -> {
            String arrowFile = assembleEprSeries(traj, horizonYear);
            collectMatchingClusters(thermalClusterProps, NuclearClusterNames::isEpr)
                    .forEach(key -> seriesByCluster.put(key, arrowFile));
        });

        smrTrajectory.ifPresent(traj -> assembleSmrAvailability(traj, horizonYear, thermalClusterProps, seriesByCluster, smrMixageByCluster));

        return new NuclearAvailabilityAssemblyResult(seriesByCluster, smrMixageByCluster);
    }

    private static Optional<TrajectoryEntity> findFirstByType(Set<TrajectoryEntity> trajectories, TrajectoryType type) {
        return trajectories.stream()
                .filter(t -> type.name().equals(t.getType()))
                .findFirst();
    }

    private static boolean isDefaultLongTermCluster(String clusterName) {
        return NuclearClusterNames.isNuclear(clusterName)
                && !NuclearClusterNames.isPeak(clusterName)
                && !NuclearClusterNames.isEpr(clusterName)
                && !NuclearClusterNames.isSmr(clusterName)
                && !NuclearClusterNames.isN4(clusterName)
                && !NuclearClusterNames.isP4(clusterName);
    }

    private static List<AreaClusterRefKey> collectMatchingClusters(Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps,
            Predicate<String> clusterNamePredicate) {
        return thermalClusterProps.keySet().stream()
                .filter(key -> FR_AREA.equalsIgnoreCase(key.area()) && clusterNamePredicate.test(key.thermalClusterRef().getName()))
                .toList();
    }

    private String assembleLongTermSeries(TrajectoryEntity ltTrajectory, String horizon, String designation) {
        Path relativePath = Path.of(properties.getNuclearLtDirectory())
                .resolve(ltTrajectory.getFileName())
                .resolve(LT_SIMU_FILE_PREFIX + horizon + XLSX_SUFFIX);
        Path ltPath = resolveValidatedNasPath(relativePath, "Invalid nuclear LT path: {0}");

        try {
            Set<String> whitelist = loadWhitelist(designation);
            List<String> sheetNames = timeSeriesReader.listSheetNames(ltPath);

            List<TimeSeriesMatrixColumn> onglets = new ArrayList<>(sheetNames.size());
            for (String sheetName : sheetNames) {
                TimeSeriesMatrix selected = timeSeriesReader.readSelectedColumnsFromXlsx(ltPath, sheetName, whitelist);
                    double[] hourly = expandDailyToHourly(sumColumnsRowWise(selected));
                    //To round to 2 decimal places
                    for (int i = 0; i < hourly.length; i++) {
                        hourly[i] = Math.round(hourly[i] * 100.0) / 100.0;
                    }
                    onglets.add(new TimeSeriesMatrixColumn(sheetName, hourly));
            }

            TimeSeriesMatrix combined = new TimeSeriesMatrix(onglets);
            byte[] bytes = nasFileService.getWriter().writeToByteArray(combined);
            return nasFileService.saveMatrixBytesToNas(bytes, ltTrajectory.getFileName() + "_lt_" + designation, properties.getNuclearAvailabilityTsOutputDirectory());
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(ltTrajectory.getFileName()))
                    .message("Failed to assemble nuclear LT availability series for trajectory: {0}")
                    .cause(e)
                    .build();
        }
    }

    private Set<String> loadWhitelist(String designation) {
        return clusterDesignationRepository.findByCluster_TypeCluster(designation)
                .stream()
                .map(d -> d.getId().getNomCluster())
                .collect(Collectors.toSet());
    }

    private static double[] sumColumnsRowWise(TimeSeriesMatrix matrix) {
        if (matrix.columns().isEmpty()) {
            return new double[MAX_ROWS_PER_YEAR];
        }
        int rowCount = matrix.getRowCount();
        double[] sums = new double[rowCount];
        for (TimeSeriesMatrixColumn column : matrix.columns()) {
            double[] values = column.values();
            for (int i = 0; i < rowCount; i++) {
                sums[i] += values[i];
            }
        }
        return sums;
    }

    private static double[] expandDailyToHourly(double[] dailyValues) {
        double[] hourly = new double[dailyValues.length * HOURS_PER_DAY];
        for (int day = 0; day < dailyValues.length; day++) {
            Arrays.fill(hourly, day * HOURS_PER_DAY, (day + 1) * HOURS_PER_DAY, dailyValues[day]);
        }
        return hourly;
    }

    /**
     * Each EPR column is already a complete, independent series (no sum needed like LT (one "SimuN" column for each MC scenario)
     * but is still stored in 365 daily format, so it gets the same
     * x24 hourly duplication, applied per column.
     */
    private String assembleEprSeries(TrajectoryEntity eprTrajectory, String horizonYear) {
        try {
            Path relativePath = resolvePrefixedRelativePath(properties.getNuclearEprDirectory(),
                    NuclearFilePrefixes.EPR_FILE_PREFIX, eprTrajectory.getFileName());
            Path eprPath = resolveValidatedNasPath(relativePath, "Invalid nuclear EPR path: {0}");

            TimeSeriesMatrix pool = nuclearTimeSeriesReader.readFromXlsx(eprPath, horizonYear, true);
            List<TimeSeriesMatrixColumn> hourlyColumns = pool.columns().stream()
                    .map(column -> new TimeSeriesMatrixColumn(column.name(), expandDailyToHourly(column.values())))
                    .toList();

            byte[] bytes = nasFileService.getWriter().writeToByteArray(new TimeSeriesMatrix(hourlyColumns));
            return nasFileService.saveMatrixBytesToNas(bytes, eprTrajectory.getFileName() + "_epr", properties.getNuclearAvailabilityTsOutputDirectory());
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(eprTrajectory.getFileName()))
                    .message("Failed to assemble nuclear EPR availability series for trajectory: {0}")
                    .cause(e)
                    .build();
        }
    }

    /**
     * SMR availability is only prepared here, not fianlized: the column pool (each column expanded
     * x24 for hourly) is converted to a single shared Arrow file, and each matched cluster gets the
     * active unit count and a composed seed string. The seeded "mixage des chroniques" that combines
     * pool columns per active unit is left for the python generator
     */
    private void assembleSmrAvailability(TrajectoryEntity smrTrajectory, String horizonYear,
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps,
            Map<AreaClusterRefKey, String> seriesByCluster,
            Map<AreaClusterRefKey, NuclearSMRMixageDTO> smrMixageByCluster) {
        List<AreaClusterRefKey> matchedClusters = collectMatchingClusters(thermalClusterProps, NuclearClusterNames::isSmr);
        if (matchedClusters.isEmpty()) {
            return;
        }

        Map<AreaClusterRefKey, Integer> unitCountByCluster = requireUnitCounts(matchedClusters, thermalClusterProps);

        List<TimeSeriesMatrixColumn> poolColumns = readSmrPool(smrTrajectory, horizonYear);
        if (poolColumns.isEmpty()) {
            return;
        }

        String arrowFile = writeSharedSmrPool(smrTrajectory, poolColumns);
        for (AreaClusterRefKey key : matchedClusters) {
            seriesByCluster.put(key, arrowFile);
            smrMixageByCluster.put(key, new NuclearSMRMixageDTO(unitCountByCluster.get(key), buildSeed(FR_AREA, key.thermalClusterRef().getName())));
        }
    }

    private static Map<AreaClusterRefKey, Integer> requireUnitCounts(List<AreaClusterRefKey> matchedClusters,
            Map<AreaClusterRefKey, ThermalClusterGenerationDto> thermalClusterProps) {
        Map<AreaClusterRefKey, Integer> unitCountByCluster = new LinkedHashMap<>();
        for (AreaClusterRefKey key : matchedClusters) {
            unitCountByCluster.put(key, requireUnitCount(thermalClusterProps.get(key), key.thermalClusterRef().getName()));
        }
        return unitCountByCluster;
    }

    private List<TimeSeriesMatrixColumn> readSmrPool(TrajectoryEntity smrTrajectory, String horizonYear) {
        try {
            Path relativePath = resolvePrefixedRelativePath(properties.getNuclearSmrDirectory(),
                    NuclearFilePrefixes.SMR_FILE_PREFIX, smrTrajectory.getFileName());
            Path smrPath = resolveValidatedNasPath(relativePath, "Invalid nuclear SMR path: {0}");

            TimeSeriesMatrix pool = nuclearTimeSeriesReader.readFromXlsx(smrPath, horizonYear, true);
            return pool.columns().stream()
                    .map(column -> new TimeSeriesMatrixColumn(column.name(), expandDailyToHourly(column.values())))
                    .toList();
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(smrTrajectory.getFileName()))
                    .message("Failed to read nuclear SMR availability pool for trajectory: {0}")
                    .cause(e)
                    .build();
        }
    }

    private String writeSharedSmrPool(TrajectoryEntity smrTrajectory, List<TimeSeriesMatrixColumn> poolColumns) {
        try {
            byte[] bytes = nasFileService.getWriter().writeToByteArray(new TimeSeriesMatrix(poolColumns));
            return nasFileService.saveMatrixBytesToNas(bytes,
                    smrTrajectory.getFileName() + "_smr", properties.getNuclearAvailabilityTsOutputDirectory());
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(smrTrajectory.getFileName()))
                    .message("Failed to write nuclear SMR availability pool for trajectory: {0}")
                    .cause(e)
                    .build();
        }
    }

    private static int requireUnitCount(ThermalClusterGenerationDto dto, String clusterName) {
        Integer unitCount = dto.getUnitCount();
        if (unitCount == null || unitCount <= 0) {
            throw BusinessException.builder()
                    .message("Nuclear SMR cluster {0} has no valid unit count for availability mixage")
                    .errorMessageArguments(List.of(clusterName))
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return unitCount;
    }

    private static String buildSeed(String zone, String clusterName) {
        return zone + clusterName + SEED_SUFFIX;
    }

    private Path resolvePrefixedRelativePath(String directoryProperty, String prefix, String storedFileName) throws IOException {
        Path directory = Path.of(directoryProperty);
        for (String candidatePrefix : List.of(prefix, prefix.toLowerCase(Locale.ROOT))) {
            Path candidate = directory.resolve(buildPrefixedFileName(candidatePrefix, storedFileName));
            if (Files.exists(resolveValidatedNasPath(candidate, "Invalid nuclear file path: {0}"))) {
                return candidate;
            }
        }
        throw new IOException("Nuclear file not found for: " + storedFileName);
    }

    private String buildPrefixedFileName(String prefix, String storedFileName) {
        String prefixed = prefix + storedFileName;
        return prefixed.toLowerCase(Locale.ROOT).endsWith(XLSX_SUFFIX) ? prefixed : prefixed + XLSX_SUFFIX;
    }

    private String extractHorizonYear(String horizon) {
        return horizon != null && horizon.contains("-") ? horizon.split("-")[1] : horizon;
    }

    private Path resolveNasPath(Path relativePath) {
        return pathSecurityUtil.resolveSafePath(
                p -> Path.of(p.getNasDirectory(), p.getTrajectoryFilePath()),
                relativePath.toString()
        );
    }

    private Path resolveValidatedNasPath(Path relativePath, String invalidPathMessage) {
        try {
            return resolveNasPath(relativePath);
        } catch (BusinessException e) {
            throw TechnicalException.builder()
                    .errorMessageArguments(List.of(relativePath.toString()))
                    .message(invalidPathMessage)
                    .cause(e)
                    .build();
        }
    }
}
