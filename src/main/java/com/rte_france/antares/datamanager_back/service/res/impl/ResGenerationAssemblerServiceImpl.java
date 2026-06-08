package com.rte_france.antares.datamanager_back.service.res.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.ResClusterCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.ResGroupEnum;
import com.rte_france.antares.datamanager_back.repository.model.ResTechnologyDistributionEntity;
import com.rte_france.antares.datamanager_back.repository.model.ResZonalDistributionEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.res.ResGenerationAssemblerService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

import static com.rte_france.antares.datamanager_back.service.res.impl.ResDomainRules.ZONAL_AREAS;
import static com.rte_france.antares.datamanager_back.service.res.impl.ResDomainRules.extractBaseArea;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResGenerationAssemblerServiceImpl implements ResGenerationAssemblerService {

    private static final String PROPERTIES = "properties";
    private static final String GROUP = "group";
    private static final String CAPACITY = "capacity";
    private static final String SERIES = "series";
    private static final String FR_AGGREGATION = "fr_aggregation";
    private static final String ZONE_WEIGHTS = "zone_weights";
    private static final String TECH_WEIGHTS_BY_ZONE = "tech_weights_by_zone";
    private static final String SERIES_BY_ZONE_AND_TECH = "series_by_zone_and_tech";
    private static final String IN_RES_GROUP_SUFFIX = " in RES group ";

    private final NasFileService nasFileService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final PathSecurityUtil pathSecurityUtil;

    @Override
    public Map<String, Map<String, Object>> assembleResProperties(StudyEntity studyEntity) {
        var trajectories = studyEntity.getTrajectories();
        if (trajectories == null || trajectories.isEmpty()) {
            return Collections.emptyMap();
        }

        var collections = collectTrajectoriesSinglePass(studyEntity);
        var capacities = collections.capacities();

        if (capacities.isEmpty()) {
            return Collections.emptyMap();
        }

        var series = createArrowSeriesForResLoad(studyEntity);
        var frSeriesIndex = indexFrSeries(series);
        var nonFrSeriesIndex = indexNonFrSeries(series);

        return processAreaClusters(capacities, collections, frSeriesIndex, nonFrSeriesIndex);
    }

    private record ClusterAggregationContext(
            String area,
            List<ResTechnologyDistributionEntity> techDistributions,
            List<ResZonalDistributionEntity> zonalDistributions,
            Map<String, ResSeriesRef> frSeriesIndex,
            Map<String, ResSeriesRef> nonFrSeriesIndex
    ) {}

    private String resolveIndexedSingleSeries(String area, String group, Map<String, ResSeriesRef> nonFrSeriesIndex) {
        var lookupKey = area.toUpperCase(Locale.ROOT) + "_" + group.toUpperCase(Locale.ROOT);
        var match = nonFrSeriesIndex.get(lookupKey);

        if (match != null) {
            return match.arrowPath();
        }

        throw BusinessException.builder()
                .message("Non-FR RES series must resolve to exactly one arrow for area/group " + area + "/" + group)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private Map<String, Map<String, Object>> processAreaClusters(
            Map<String, List<ResClusterCapacityEntity>> capacities,
            TrajectoryCollections collections,
            Map<String, ResSeriesRef> frSeriesIndex,
            Map<String, ResSeriesRef> nonFrSeriesIndex
    ) {
        return capacities.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    var area = entry.getKey();
                    var techs = collections.technologies().getOrDefault(area, Collections.emptyList());
                    var zonals = collections.zonals().getOrDefault(area, Collections.emptyList());
                    var context = new ClusterAggregationContext(area, techs, zonals, frSeriesIndex, nonFrSeriesIndex);
                    return buildClustersForArea(entry.getValue(), context);
                },
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }

    private Map<String, Object> buildClustersForArea(List<ResClusterCapacityEntity> capacities, ClusterAggregationContext context) {
        return capacities.stream()
                .collect(Collectors.groupingBy(cap -> normalizeGroup(cap.getGroupe()), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> buildGroupCluster(entry.getKey(), entry.getValue(), context),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private Map<String, Object> buildGroupCluster(String groupKey, List<ResClusterCapacityEntity> entities, ClusterAggregationContext context) {
        var installedPower = entities.stream()
                .map(ResClusterCapacityEntity::getCapacityByYear)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        var clusterPropertiesMap = new LinkedHashMap<String, Object>();
        clusterPropertiesMap.put(CAPACITY, installedPower);
        clusterPropertiesMap.put(GROUP, groupKey);

        var clusterMap = new LinkedHashMap<String, Object>();
        clusterMap.put(PROPERTIES, clusterPropertiesMap);

        if (ResDomainRules.FR_AREA.equalsIgnoreCase(context.area())) {
            clusterMap.put(SERIES, Collections.emptyList());
            clusterMap.put(FR_AGGREGATION, buildFrAggregation(
                    groupKey, installedPower, context.techDistributions(), context.zonalDistributions(), context.frSeriesIndex()
            ));
        } else {
            var seriesPath = resolveIndexedSingleSeries(context.area(), groupKey, context.nonFrSeriesIndex());
            clusterMap.put(SERIES, List.of(seriesPath));
        }

        return clusterMap;
    }

    private Map<String, Object> buildFrAggregation(
            String normalizedGroup,
            double installedPower,
            List<ResTechnologyDistributionEntity> technologyDistributions,
            List<ResZonalDistributionEntity> zonalDistributions,
            Map<String, ResSeriesRef> frSeriesIndex
    ) {
        var zoneWeights = calculateZoneWeights(normalizedGroup, zonalDistributions);
        var techWeightsByZone = new LinkedHashMap<String, Map<String, Double>>();
        var seriesByZoneAndTech = new LinkedHashMap<String, Map<String, String>>();

        technologyDistributions.stream()
                .filter(entity -> normalizedGroup.equals(normalizeGroup(entity.getGroupe())))
                .forEach(entity -> processTechnologyEntity(
                        entity, normalizedGroup, zoneWeights, techWeightsByZone, seriesByZoneAndTech, frSeriesIndex
                ));

        validateFrAggregation(normalizedGroup, installedPower, zoneWeights, techWeightsByZone, seriesByZoneAndTech);

        var aggregation = new LinkedHashMap<String, Object>();
        aggregation.put(ZONE_WEIGHTS, zoneWeights);
        aggregation.put(TECH_WEIGHTS_BY_ZONE, techWeightsByZone);
        aggregation.put(SERIES_BY_ZONE_AND_TECH, seriesByZoneAndTech);
        return aggregation;
    }

    private Map<String, Double> calculateZoneWeights(
            String normalizedGroup,
            List<ResZonalDistributionEntity> zonalDistributions
    ) {
        return zonalDistributions.stream()
                .filter(e -> normalizedGroup.equals(normalizeGroup(e.getGroupe())))
                .collect(Collectors.toMap(
                        e -> canonicalFrZone(e.getPecdZone()),
                        e -> normalizeWeight(e.getCapacityByYear(), "zonal", e.getPecdZone(), e.getGroupe()),
                        Double::sum,
                        LinkedHashMap::new
                ));
    }

    private void processTechnologyEntity(
            ResTechnologyDistributionEntity entity,
            String normalizedGroup,
            Map<String, Double> zoneWeights,
            Map<String, Map<String, Double>> techWeightsByZone,
            Map<String, Map<String, String>> seriesByZoneAndTech,
            Map<String, ResSeriesRef> frSeriesIndex
    ) {
        var zone = canonicalFrZone(entity.getPecdZone());
        var zoneWeight = zoneWeights.getOrDefault(zone, 0d);

        if (zoneWeight <= 0d) {
            return;
        }

        var technology = toKey(entity.getPecdTechnology());
        var weight = normalizeWeight(entity.getCapacityByYear(), "technology", zone, entity.getPecdTechnology());

        techWeightsByZone.computeIfAbsent(zone, k -> new LinkedHashMap<>()).put(technology, weight);

        var arrowPath = resolveIndexedFrSeries(zone, normalizedGroup, technology, frSeriesIndex);
        seriesByZoneAndTech.computeIfAbsent(zone, k -> new LinkedHashMap<>()).put(technology, arrowPath);
    }

    private String resolveIndexedFrSeries(
            String zone,
            String group,
            String technology,
            Map<String, ResSeriesRef> frSeriesIndex
    ) {
        var candidateKeys = buildFrTechnologyCandidateKeys(group, technology);

        for (var candidateKey : candidateKeys) {
            var lookupKey = zone.toUpperCase(Locale.ROOT) + "_" + group.toUpperCase(Locale.ROOT) + "_" + candidateKey.toUpperCase(Locale.ROOT);
            var match = frSeriesIndex.get(lookupKey);
            if (match != null) {
                return match.arrowPath();
            }
        }

        throw BusinessException.builder()
                .message("FR RES series resolution must return exactly one arrow for zone " + zone + " and technology " + technology)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private void validateFrAggregation(
            String normalizedGroup,
            double installedPower,
            Map<String, Double> zoneWeights,
            Map<String, Map<String, Double>> techWeightsByZone,
            Map<String, Map<String, String>> seriesByZoneAndTech
    ) {
        if (installedPower <= 0d) {
            return;
        }

        if (zoneWeights.isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing FR aggregation data for RES group " + normalizedGroup)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        zoneWeights.forEach((zone, weight) ->
                validateZoneAggregation(normalizedGroup, zone, weight, techWeightsByZone, seriesByZoneAndTech));
    }

    private void validateZoneAggregation(
            String normalizedGroup,
            String zone,
            Double zoneWeight,
            Map<String, Map<String, Double>> techWeightsByZone,
            Map<String, Map<String, String>> seriesByZoneAndTech
    ) {
        if (zoneWeight != null && zoneWeight > 0d) {
            Map<String, Double> techWeights = techWeightsByZone.get(zone);

            // A zone with zoneWeight > 0 must have technology distribution rows.
            if (techWeights == null || techWeights.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Missing FR technology rows for active zone {} in RES group {}", zone, normalizedGroup);
                }
                throw BusinessException.builder()
                        .message("Missing FR technology mapping for zone " + zone + IN_RES_GROUP_SUFFIX + normalizedGroup)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            if (log.isDebugEnabled()) {
                Map<String, String> techSeries = seriesByZoneAndTech.get(zone);
                log.debug("Validated FR tech rows for zone {} in RES group {}: techCount={}, techSum={}, seriesCount={}",
                        zone,
                        normalizedGroup,
                        techWeights.size(),
                        techWeights.values().stream().mapToDouble(Double::doubleValue).sum(),
                        techSeries == null ? 0 : techSeries.size());
            }
        } else if (log.isDebugEnabled()) {
            log.debug("Zone {} has zoneWeight={}, skipping tech/series validation", zone, zoneWeight);
        }
    }

    private Set<String> buildFrTechnologyCandidateKeys(String group, String technology) {
        String normalizedGroup = toKey(group);
        String normalizedTechnology = toKey(technology);

        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(normalizedTechnology);

        String groupPrefix = normalizedGroup + "_";
        if (normalizedTechnology.startsWith(groupPrefix) && normalizedTechnology.length() > groupPrefix.length()) {
            candidates.add(normalizedTechnology.substring(groupPrefix.length()));
        }

        return candidates;
    }

    private List<ResSeriesRef> createArrowSeriesForResLoad(StudyEntity studyEntity) {
        List<TrajectoryEntity> resLoadTrajectories = studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.RES_LOAD.name().equals(t.getType()))
                .toList();

        if (resLoadTrajectories.isEmpty()) {
            return Collections.emptyList();
        }

        Path base = Path.of(antaresDataManagerProperties.getNasDirectory())
                .resolve(antaresDataManagerProperties.getTrajectoryFilePath())
                .resolve(antaresDataManagerProperties.getResLoadDirectory())
                .normalize();

        List<ResSeriesRef> result = new ArrayList<>();

        // track already processed trajectories
        Set<String> processedTrajectories = new HashSet<>();

        for (TrajectoryEntity trajectory : resLoadTrajectories) {
            String trajectoryFileName = trajectory.getFileName();

            if (trajectoryFileName != null && !trajectoryFileName.isBlank() && processedTrajectories.add(trajectoryFileName)) {
                resolveSeriesInTrajectory(trajectory, base, result);
            }
        }

        return result;
    }

    private void resolveSeriesInTrajectory(TrajectoryEntity trajectory, Path base, List<ResSeriesRef> result) {
        String trajectoryFileName = trajectory.getFileName();
        if (trajectoryFileName == null || trajectoryFileName.isBlank()) {
            return;
        }

        try {
            pathSecurityUtil.validatePathFromBaseDir(trajectoryFileName, AntaresDataManagerProperties::getResLoadDirectory);
            Path trajectoryRoot = base.resolve(trajectoryFileName).normalize();

            if (!trajectoryRoot.startsWith(base) || !Files.exists(trajectoryRoot)) {
                throw BusinessException.builder()
                        .message("Invalid RES load trajectory path: " + trajectoryFileName)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            try (var walk = Files.walk(trajectoryRoot)) {
                walk.filter(file -> {
                            try {
                                return ResDomainRules.isBaselineTrajectoryFile(file, Files.readAttributes(file, BasicFileAttributes.class));
                            } catch (IOException e) {
                                log.warn("Could not read attributes for RES load file {}, skipping", file, e);
                                return false;
                            }
                        })
                        .forEach(file -> createSeriesFromFile(file, trajectoryRoot, result));
            }
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not list RES load trajectory files for " + trajectoryFileName)
                    .cause(e)
                    .build();
        }
    }

    private void createSeriesFromFile(Path file, Path trajectoryRoot, List<ResSeriesRef> result) {
        String rel = trajectoryRoot.relativize(file).toString();
        parseSeriesKeyFromRelativePath(rel).ifPresent(parsedKey -> {
            String outputDir = antaresDataManagerProperties.getResTsOutputDirectory();
            try {
                String arrowName = nasFileService.readAndSaveMatrixToNas(file, outputDir, null, true);
                result.add(new ResSeriesRef(
                        toKey(rel.replace('\\', '/')),
                        arrowName,
                        parsedKey.area(),
                        parsedKey.group(),
                        parsedKey.zone(),
                        parsedKey.technology()
                ));
            } catch (IOException e) {
                throw TechnicalException.builder()
                        .message("Could not generate RES arrow file from " + file)
                        .cause(e)
                        .build();
            }
        });
    }

    private Optional<ParsedSeriesKey> parseSeriesKeyFromRelativePath(String relativePath) {
        String fileName = Path.of(relativePath).getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String normalizedBaseName = toKey(baseName);
        List<String> tokens = Arrays.stream(normalizedBaseName.split("_"))
                .filter(token -> !token.isBlank())
                .toList();
        return parseSeriesKey(tokens);
    }

    private Optional<ParsedSeriesKey> parseSeriesKey(List<String> tokens) {
        if (tokens.size() < 3) {
            return Optional.empty();
        }

        Optional<ParsedSeriesKey> styleA = parseSeriesKeyFromStyleA(tokens);
        if (styleA.isPresent()) {
            return styleA;
        }
        return parseSeriesKeyFromStyleB(tokens);
    }

    private Optional<ParsedSeriesKey> parseSeriesKeyFromStyleA(List<String> tokens) {
        // Case A: <groupPrefix>_<areaOrZone>_<groupSuffix>_[tech...]_[horizon]
        String areaOrZone = tokens.get(1);
        Optional<String> normalizedGroup = tryNormalizeGroup(tokens.get(0), tokens.get(2));
        if (normalizedGroup.isEmpty()) {
            return Optional.empty();
        }
        return buildParsedSeriesKey(areaOrZone, normalizedGroup.get(), tokens, 3);
    }

    private Optional<ParsedSeriesKey> parseSeriesKeyFromStyleB(List<String> tokens) {
        // Casse B: <groupPrefix>_<groupSuffix>_<areaOrZone>_[tech...]_[horizon]
        if (tokens.size() < 4) {
            return Optional.empty();
        }
        String areaOrZone = tokens.get(2);
        Optional<String> normalizedGroup = tryNormalizeGroup(tokens.get(0), tokens.get(1));
        if (normalizedGroup.isEmpty()) {
            return Optional.empty();
        }
        return buildParsedSeriesKey(areaOrZone, normalizedGroup.get(), tokens, 3);
    }

    private Optional<ParsedSeriesKey> buildParsedSeriesKey(String areaOrZone, String normalizedGroup, List<String> tokens, int technologyStartIndex) {
        if (areaOrZone == null || areaOrZone.isBlank()) {
            return Optional.empty();
        }

        String normalizedToken = areaOrZone.toUpperCase(Locale.ROOT);
        String baseArea = extractBaseArea(normalizedToken);

        if (ZONAL_AREAS.contains(baseArea)) {
            if (normalizedToken.equals(baseArea)) {
                log.warn("Malformed zonal series skipped. Expected zone (like: {}01), but found global zone: {}", baseArea, String.join("_", tokens));
                return Optional.empty();
            }

            List<String> technologyTokens = trimTrailingYearTokens(tokens.subList(technologyStartIndex, tokens.size()));
            if (technologyTokens.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ParsedSeriesKey(baseArea, normalizedGroup, normalizedToken, String.join("_", technologyTokens)));
        }

        // default behavior for other areas (AT, BE, DE, ..)
        return Optional.of(new ParsedSeriesKey(normalizedToken, normalizedGroup, null, null));
    }

    private List<String> trimTrailingYearTokens(List<String> tokens) {
        List<String> trimmed = new ArrayList<>(tokens);
        if (hasTrailingHorizonPair(trimmed)) {
            trimmed.remove(trimmed.size() - 1);
            trimmed.remove(trimmed.size() - 1);
        }
        return trimmed;
    }

    private boolean hasTrailingHorizonPair(List<String> tokens) {
        if (tokens.size() < 2) {
            return false;
        }
        String last = tokens.get(tokens.size() - 1);
        String previous = tokens.get(tokens.size() - 2);
        return isYearToken(previous) && isYearToken(last);
    }

    private boolean isYearToken(String token) {
        return token != null && token.matches("\\d{4}");
    }

    private Optional<String> tryNormalizeGroup(String first, String second) {
        try {
            return Optional.of(normalizeGroup(first + "_" + second));
        } catch (BusinessException ignored) {
            return Optional.empty();
        }
    }

    private String normalizeGroup(String group) {
        try {
            return ResGroupEnum.normalizeForGenerator(group);
        } catch (IllegalArgumentException ex) {
            throw BusinessException.builder()
                    .message(ex.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private String canonicalFrZone(String zone) {
        String normalized = zone == null ? "" : zone.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("FR\\d+")) {
            throw BusinessException.builder()
                    .message("Invalid FR zone key: " + zone)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return normalized;
    }


    private double normalizeWeight(Number rawWeight, String weightType, String zone, String key) {
        if (rawWeight == null) {
            throw BusinessException.builder()
                    .message("Missing RES " + weightType + " weight for " + zone + "/" + key)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        double weight = rawWeight.doubleValue();
        if (weight < 0) {
            throw BusinessException.builder()
                    .message("Negative RES " + weightType + " weight is forbidden for " + zone + "/" + key)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        if (weight > 1d) {
            weight = weight / 100d;
        }
        return BigDecimal.valueOf(weight).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    private String toKey(String value) {
        return Objects.toString(value, "").trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replaceAll("\\s+", "_");
    }

    private record ResSeriesRef(String sourceKey, String arrowPath, String area, String group, String zone, String technology) { }

    private record ParsedSeriesKey(String area, String group, String zone, String technology) {
    }

    private Map<String, ResSeriesRef> indexNonFrSeries(List<ResSeriesRef> series) {
        return series.stream()
                .filter(ref -> !ResDomainRules.FR_AREA.equalsIgnoreCase(ref.area()))
                .collect(Collectors.toMap(
                        ref -> ref.area().toUpperCase(Locale.ROOT) + "_" + ref.group().toUpperCase(Locale.ROOT),
                        ref -> ref,
                        (existing, replacement) -> {
                            throw BusinessException.builder()
                                    .message("Duplicate non-FR series found")
                                    .httpStatus(HttpStatus.BAD_REQUEST)
                                    .build();
                        }
                ));
    }

    private Map<String, ResSeriesRef> indexFrSeries(List<ResSeriesRef> series) {
        return series.stream()
                .filter(ref -> ResDomainRules.FR_AREA.equalsIgnoreCase(ref.area()))
                .collect(Collectors.toMap(
                        ref -> ref.zone().toUpperCase(Locale.ROOT) + "_" + ref.group().toUpperCase(Locale.ROOT) + "_" + ref.technology().toUpperCase(Locale.ROOT),
                        ref -> ref,
                        (existing, replacement) -> existing
                ));
    }

    private record TrajectoryCollections(
            Map<String, List<ResClusterCapacityEntity>> capacities,
            Map<String, List<ResTechnologyDistributionEntity>> technologies,
            Map<String, List<ResZonalDistributionEntity>> zonals
    ) {}

    private TrajectoryCollections collectTrajectoriesSinglePass(StudyEntity studyEntity) {
        var capacities = new LinkedHashMap<String, List<ResClusterCapacityEntity>>();
        var technologies = new LinkedHashMap<String, List<ResTechnologyDistributionEntity>>();
        var zonals = new LinkedHashMap<String, List<ResZonalDistributionEntity>>();

        studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .forEach(trajectory -> routeTrajectoryData(trajectory, capacities, technologies, zonals));

        return new TrajectoryCollections(capacities, technologies, zonals);
    }

    private void routeTrajectoryData(
            TrajectoryEntity trajectory,
            Map<String, List<ResClusterCapacityEntity>> capacities,
            Map<String, List<ResTechnologyDistributionEntity>> technologies,
            Map<String, List<ResZonalDistributionEntity>> zonals
    ) {
        var type = TrajectoryType.valueOf(trajectory.getType());

        switch (type) {
            case RES_CAPACITY -> extractCapacities(trajectory, capacities);
            case RES_TECHNOLOGY_DISTRIBUTION -> extractTechnologies(trajectory, technologies);
            case RES_ZONAL_DISTRIBUTION -> extractZonals(trajectory, zonals);
            default -> { /* ignore */}
        }
    }

    private void extractCapacities(
            TrajectoryEntity trajectory,
            Map<String, List<ResClusterCapacityEntity>> accumulator
    ) {
        var entities = trajectory.getResClusterCapacityEntities();
        if (entities == null) return;

        entities.stream()
                .filter(this::isValidCapacityEntity)
                .forEach(e -> accumulateByArea(e, e.getArea(), accumulator));
    }

    private void extractTechnologies(
            TrajectoryEntity trajectory,
            Map<String, List<ResTechnologyDistributionEntity>> accumulator
    ) {
        var entities = trajectory.getResTechnologyDistributionCapacityEntities();
        if (entities == null) return;

        entities.stream()
                .filter(this::isValidTechnologyEntity)
                .forEach(e -> accumulateByArea(e, e.getArea(), accumulator));
    }

    private boolean isValidTechnologyEntity(ResTechnologyDistributionEntity e) {
        return e != null && e.getArea() != null && e.getGroupe() != null
                && e.getPecdZone() != null && e.getPecdTechnology() != null;
    }

    private void extractZonals(
            TrajectoryEntity trajectory,
            Map<String, List<ResZonalDistributionEntity>> accumulator
    ) {
        var entities = trajectory.getResZonalDistributionCapacityEntities();
        if (entities == null) return;

        entities.stream()
                .filter(this::isValidZonalEntity)
                .forEach(e -> accumulateByArea(e, e.getArea(), accumulator));
    }

    private boolean isValidZonalEntity(ResZonalDistributionEntity e) {
        return e != null && e.getArea() != null
                && e.getGroupe() != null && e.getPecdZone() != null;
    }

    private boolean isValidCapacityEntity(ResClusterCapacityEntity e) {
        return e != null && Boolean.TRUE.equals(e.getToUse())
                && e.getArea() != null && e.getGroupe() != null && e.getCluster() != null;
    }

    private <T> void accumulateByArea(T entity, String rawArea, Map<String, List<T>> map) {
        var normalizedArea = rawArea.toUpperCase(Locale.ROOT);
        map.computeIfAbsent(normalizedArea, k -> new ArrayList<>()).add(entity);
    }
}
