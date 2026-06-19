package com.rte_france.antares.datamanager_back.service.res.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.ResClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.ResClusterPropertiesDto;
import com.rte_france.antares.datamanager_back.dto.ResFrAggregationDto;
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

    private static final String IN_RES_GROUP_SUFFIX = " in RES group ";
    private static final double MAX_COEFF_SUM = 1d;

    private final NasFileService nasFileService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final PathSecurityUtil pathSecurityUtil;

    private record TrajectoryCollections(
            Map<String, List<ResClusterCapacityEntity>> capacities,
            Map<String, List<ResTechnologyDistributionEntity>> technologies,
            Map<String, List<ResZonalDistributionEntity>> zonals
    ) {}

    private record SeriesLookup(Map<String, ResSeriesRef> index, Map<Path, String> arrowCache) {}

    private record FrAggregationAccumulator(
            Map<String, Double> zoneWeights,
            Map<String, Map<String, Double>> techWeightsByZone,
            Map<String, Map<String, String>> seriesByZoneAndTech
    ) {}

    private record ClusterAggregationContext(
            String area,
            List<ResTechnologyDistributionEntity> techDistributions,
            List<ResZonalDistributionEntity> zonalDistributions,
            SeriesLookup frLookup,
            SeriesLookup nonFrLookup
    ) {}

    private record ResSeriesRef(String trajectoryFileName, String sourceKey, Path filePath, String area, String group, String cluster, String zone, String technology, boolean fromTechnoTrajectory, boolean fromOthersArea) { }

    private record SeriesScanContext(String trajectoryFileName, boolean fromTechnoTrajectory, boolean fromOthersArea, String linkedArea, String linkedTech) { }

    private record ParsedSeriesKey(String area, String group, String cluster, String zone, String technology) { }

    private record ClusterGroupKey(String group, String cluster) {}

    @Override
    public Map<String, Map<String, ResClusterGenerationDto>> assembleResProperties(StudyEntity studyEntity) {
        var trajectories = studyEntity.getTrajectories();
        if (trajectories == null || trajectories.isEmpty()) {
            return Collections.emptyMap();
        }

        var collections = collectTrajectoriesSinglePass(studyEntity);
        var capacities = collections.capacities();

        if (capacities.isEmpty()) {
            return Collections.emptyMap();
        }

        var series = collectResLoadSeriesRefs(studyEntity);
        var arrowCache = new HashMap<Path, String>();
        var frLookup = new SeriesLookup(indexFrSeries(series), arrowCache);
        var nonFrLookup = new SeriesLookup(indexNonFrSeries(series), arrowCache);

        return processAreaClusters(capacities, collections, frLookup, nonFrLookup);
    }

    private String resolveIndexedSingleSeries(String area, String group, String cluster, SeriesLookup nonFrLookup) {
        var lookupKey = area.toUpperCase(Locale.ROOT) + "_" + group.toUpperCase(Locale.ROOT) + "_" + cluster.toUpperCase(Locale.ROOT);
        var match = nonFrLookup.index().get(lookupKey);

        if (match != null) {
            return convertSeriesToArrowIfAbsent(match, nonFrLookup.arrowCache());
        }

        throw BusinessException.builder()
                .message("No load-factor series found for area '" + area + "', group '" + group
                        + "', cluster '" + cluster + "'. Link a load-factor trajectory that contains a series file for this area and group.")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private String convertSeriesToArrowIfAbsent(ResSeriesRef ref, Map<Path, String> arrowCache) {
        return arrowCache.computeIfAbsent(ref.filePath(), filePath -> {
            try {
                return nasFileService.readAndSaveMatrixToNas(filePath, antaresDataManagerProperties.getResTsOutputDirectory(), null, true);
            } catch (IOException e) {
                throw TechnicalException.builder()
                        .message("Could not generate RES arrow file from " + filePath)
                        .cause(e)
                        .build();
            }
        });
    }

    private Map<String, Map<String, ResClusterGenerationDto>> processAreaClusters(
            Map<String, List<ResClusterCapacityEntity>> capacities,
            TrajectoryCollections collections,
            SeriesLookup frLookup,
            SeriesLookup nonFrLookup
    ) {
        return capacities.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    var area = entry.getKey();
                    var techs = collections.technologies().getOrDefault(area, Collections.emptyList());
                    var zonals = collections.zonals().getOrDefault(area, Collections.emptyList());
                    var context = new ClusterAggregationContext(area, techs, zonals, frLookup, nonFrLookup);
                    return buildClustersForArea(entry.getValue(), context);
                },
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }

    private Map<String, ResClusterGenerationDto> buildClustersForArea(List<ResClusterCapacityEntity> capacities, ClusterAggregationContext context) {
        var byGroupAndCluster = capacities.stream()
                .collect(Collectors.groupingBy(
                        e -> new ClusterGroupKey(normalizeGroup(e.getGroupe()), e.getCluster()),
                        LinkedHashMap::new, Collectors.toList()));

        var clusterNamesSharedAcrossGroups = byGroupAndCluster.keySet().stream()
                .collect(Collectors.groupingBy(ClusterGroupKey::cluster, Collectors.mapping(ClusterGroupKey::group, Collectors.toSet())))
                .entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        return byGroupAndCluster.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> clusterNamesSharedAcrossGroups.contains(entry.getKey().cluster())
                                ? entry.getKey().cluster() + "_" + entry.getKey().group()
                                : entry.getKey().cluster(),
                        entry -> buildClusterEntry(entry.getKey().cluster(), entry.getValue(), context),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private ResClusterGenerationDto buildClusterEntry(String clusterName, List<ResClusterCapacityEntity> entities, ClusterAggregationContext context) {
        var installedPower = entities.stream()
                .map(ResClusterCapacityEntity::getCapacityByYear)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        var groupKey = normalizeGroup(entities.getFirst().getGroupe());
        var clusterProperties = new ResClusterPropertiesDto(installedPower, groupKey);

        if (ResDomainRules.FR_AREA.equalsIgnoreCase(context.area())) {
            var frAggregation = buildFrAggregation(
                    groupKey, clusterName, installedPower, context.techDistributions(), context.zonalDistributions(), context.frLookup()
            );
            return new ResClusterGenerationDto(clusterProperties, Collections.emptyList(), frAggregation);
        } else {
            var seriesPath = resolveIndexedSingleSeries(context.area(), groupKey, clusterName, context.nonFrLookup());
            return new ResClusterGenerationDto(clusterProperties, List.of(seriesPath), null);
        }
    }

    private ResFrAggregationDto buildFrAggregation(
            String normalizedGroup,
            String cluster,
            double installedPower,
            List<ResTechnologyDistributionEntity> technologyDistributions,
            List<ResZonalDistributionEntity> zonalDistributions,
            SeriesLookup frLookup
    ) {
        var zoneWeights = calculateZoneWeights(normalizedGroup, zonalDistributions);
        validateZonalCoefficientSum(normalizedGroup, cluster, zoneWeights);

        var accumulator = new FrAggregationAccumulator(zoneWeights, new LinkedHashMap<>(), new LinkedHashMap<>());

        technologyDistributions.stream()
                .filter(entity -> normalizedGroup.equals(normalizeGroup(entity.getGroupe())))
                .filter(entity -> cluster.equalsIgnoreCase(entity.getCluster()))
                .forEach(entity -> processTechnologyEntity(entity, normalizedGroup, cluster, accumulator, frLookup));

        validateTechnologyCoefficientSums(normalizedGroup, cluster, accumulator.techWeightsByZone());
        validateFrAggregation(normalizedGroup, cluster, installedPower, accumulator);

        return new ResFrAggregationDto(accumulator.zoneWeights(), accumulator.techWeightsByZone(), accumulator.seriesByZoneAndTech());
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

    private void validateZonalCoefficientSum(String normalizedGroup, String cluster, Map<String, Double> zoneWeights) {
        double sum = zoneWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum > MAX_COEFF_SUM) {
            throw BusinessException.builder()
                    .message("Invalid zonal distribution for RES group '" + normalizedGroup + "', cluster '" + cluster
                            + "': PECD zone coeffs sum to " + formatAsPercentage(sum)
                            + ", but must not be over 100%. Check the zonal distribution file for this group/cluster and correct the zone percentages so they total at most 100%.")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateTechnologyCoefficientSums(String normalizedGroup, String cluster, Map<String, Map<String, Double>> techWeightsByZone) {
        techWeightsByZone.forEach((zone, techWeights) -> {
            double sum = techWeights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (sum > MAX_COEFF_SUM) {
                throw BusinessException.builder()
                        .message("Invalid technology distribution for RES group '" + normalizedGroup + "', cluster '" + cluster
                                + "', zone '" + zone + "': technology coeffs sum to " + formatAsPercentage(sum)
                                + ", but must not be over 100%. Check the technology distribution file for this zone and correct the technology percentages so they total at most 100%.")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        });
    }

    private String formatAsPercentage(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100d);
    }

    private void processTechnologyEntity(
            ResTechnologyDistributionEntity entity,
            String normalizedGroup,
            String cluster,
            FrAggregationAccumulator accumulator,
            SeriesLookup frLookup
    ) {
        var zone = canonicalFrZone(entity.getPecdZone());
        var zoneWeight = accumulator.zoneWeights().getOrDefault(zone, 0d);

        if (zoneWeight <= 0d) {
            return;
        }

        var technology = toKey(entity.getPecdTechnology());
        var weight = normalizeWeight(entity.getCapacityByYear(), "technology", zone, entity.getPecdTechnology());

        accumulator.techWeightsByZone().computeIfAbsent(zone, k -> new LinkedHashMap<>()).put(technology, weight);

        var arrowPath = resolveIndexedFrSeries(zone, normalizedGroup, cluster, technology, frLookup);
        accumulator.seriesByZoneAndTech().computeIfAbsent(zone, k -> new LinkedHashMap<>()).put(technology, arrowPath);
    }

    private String resolveIndexedFrSeries(
            String zone,
            String group,
            String cluster,
            String technology,
            SeriesLookup frLookup
    ) {
        var candidateKeys = buildFrTechnologyCandidateKeys(group, technology);

        for (var candidateKey : candidateKeys) {
            var lookupKey = zone.toUpperCase(Locale.ROOT) + "_" + group.toUpperCase(Locale.ROOT) + "_"
                    + cluster.toUpperCase(Locale.ROOT) + "_" + candidateKey.toUpperCase(Locale.ROOT);
            var match = frLookup.index().get(lookupKey);
            if (match != null) {
                return convertSeriesToArrowIfAbsent(match, frLookup.arrowCache());
            }
        }

        throw BusinessException.builder()
                .message("No load-factor series found for FR zone '" + zone + "', cluster '" + cluster
                        + "', technology '" + technology
                        + "'. The load-factor trajectory must include a series file for this zone and technology.")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private void validateFrAggregation(
            String normalizedGroup,
            String cluster,
            double installedPower,
            FrAggregationAccumulator accumulator
    ) {
        if (installedPower <= 0d) {
            return;
        }

        if (accumulator.zoneWeights().isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing FR aggregation data for RES group " + normalizedGroup + ", cluster " + cluster)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        accumulator.zoneWeights().forEach((zone, weight) ->
                validateZoneAggregation(normalizedGroup, cluster, zone, weight, accumulator));
    }

    private void validateZoneAggregation(
            String normalizedGroup,
            String cluster,
            String zone,
            Double zoneWeight,
            FrAggregationAccumulator accumulator
    ) {
        if (zoneWeight != null && zoneWeight > 0d) {
            Map<String, Double> techWeights = accumulator.techWeightsByZone().get(zone);

            // A zone with zoneWeight > 0 must have technology distribution rows.
            if (techWeights == null || techWeights.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Missing FR technology rows for active zone {} in RES group {}, cluster {}", zone, normalizedGroup, cluster);
                }
                throw BusinessException.builder()
                        .message("Missing FR technology mapping for zone " + zone + IN_RES_GROUP_SUFFIX + normalizedGroup + ", cluster " + cluster)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            if (log.isDebugEnabled()) {
                Map<String, String> techSeries = accumulator.seriesByZoneAndTech().get(zone);
                log.debug("Validated FR tech rows for zone {} in RES group {}, cluster {}: techCount={}, techSum={}, seriesCount={}",
                        zone,
                        normalizedGroup,
                        cluster,
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

    private List<ResSeriesRef> collectResLoadSeriesRefs(StudyEntity studyEntity) {
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
        Set<String> processedLinks = new HashSet<>();
        resLoadTrajectories.forEach(t -> processResLoadLink(t, base, result, processedLinks));
        return result;
    }

    private void processResLoadLink(TrajectoryEntity trajectory, Path base, List<ResSeriesRef> result, Set<String> processedLinks) {
        String fileName = trajectory.getFileName();
        if (fileName == null || fileName.isBlank()) return;

        String normalizedArea = toKey(trajectory.getArea());
        String normalizedTech = toKey(trajectory.getTechnology());
        String linkKey = fileName + "|" + normalizedArea + "|" + normalizedTech;

        boolean fromOthersArea = ResDomainRules.OTHERS_AREA.equalsIgnoreCase(normalizedArea);
        boolean isNewLink = processedLinks.add(linkKey);
        log.debug("RES load link: file='{}' area=[{}] tech=[{}] linkKey='{}' new={}",
                fileName, trajectory.getArea(), trajectory.getTechnology(), linkKey, isNewLink);
        if (isNewLink) {
            var scanContext = new SeriesScanContext(fileName, !normalizedTech.isBlank(), fromOthersArea,
                    normalizedArea.toUpperCase(Locale.ROOT), normalizedTech.toUpperCase(Locale.ROOT));
            resolveSeriesInTrajectory(scanContext, base, result);
        }
    }

    private void resolveSeriesInTrajectory(SeriesScanContext scanContext, Path base, List<ResSeriesRef> result) {
        try {
            pathSecurityUtil.validatePathFromBaseDir(scanContext.trajectoryFileName(), AntaresDataManagerProperties::getResLoadDirectory);
            Path trajectoryRoot = base.resolve(scanContext.trajectoryFileName()).normalize();

            if (!trajectoryRoot.startsWith(base) || !Files.exists(trajectoryRoot)) {
                throw BusinessException.builder()
                        .message("Invalid RES load trajectory path: " + scanContext.trajectoryFileName())
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
                        .forEach(file -> createSeriesFromFile(file, trajectoryRoot, scanContext, result));
            }
        } catch (IOException e) {
            throw TechnicalException.builder()
                    .message("Could not list RES load trajectory files for " + scanContext.trajectoryFileName())
                    .cause(e)
                    .build();
        }
    }

    private void createSeriesFromFile(Path file, Path trajectoryRoot, SeriesScanContext scanContext, List<ResSeriesRef> result) {
        String rel = trajectoryRoot.relativize(file).toString();
        parseSeriesKeyFromRelativePath(rel).ifPresent(parsedKey -> {
            if (!scanContext.fromOthersArea() && scanContext.linkedArea() != null && !scanContext.linkedArea().isBlank() && !scanContext.linkedArea().equalsIgnoreCase(parsedKey.area())) {
                return;
            }
            if (scanContext.linkedTech() != null && !scanContext.linkedTech().isBlank() && !toKey(scanContext.linkedTech()).equalsIgnoreCase(parsedKey.group())) {
                return;
            }
            result.add(new ResSeriesRef(
                    scanContext.trajectoryFileName(),
                    toKey(rel.replace('\\', '/')),
                    file,
                    parsedKey.area(),
                    parsedKey.group(),
                    parsedKey.cluster(),
                    parsedKey.zone(),
                    parsedKey.technology(),
                    scanContext.fromTechnoTrajectory(),
                    scanContext.fromOthersArea()
            ));
        });
    }

    private Optional<ParsedSeriesKey> parseSeriesKeyFromRelativePath(String relativePath) {
        Path rel = Path.of(relativePath.replace('\\', '/'));
        if (rel.getNameCount() < 3) return Optional.empty();

        String groupFolder = rel.getName(0).toString();
        String clusterFolder = rel.getName(1).toString();
        String normalizedGroup;
        try {
            normalizedGroup = normalizeGroup(groupFolder);
        } catch (BusinessException ignored) {
            return Optional.empty();
        }

        String fileName = rel.getFileName().toString();
        int ext = fileName.lastIndexOf('.');
        String baseName = ext > 0 ? fileName.substring(0, ext) : fileName;

        String[] clusterTokens = toKey(clusterFolder).split("_");
        String[] allTokens = toKey(baseName).split("_");

        if (allTokens.length < clusterTokens.length) return Optional.empty();
        for (int i = 0; i < clusterTokens.length; i++) {
            if (!clusterTokens[i].equals(allTokens[i])) return Optional.empty();
        }

        List<String> remaining = Arrays.stream(allTokens, clusterTokens.length, allTokens.length)
                .filter(t -> !t.isBlank())
                .toList();
        if (remaining.isEmpty()) return Optional.empty();

        String areaOrZone = remaining.get(0).toUpperCase(Locale.ROOT);
        String baseArea = extractBaseArea(areaOrZone);

        if (ZONAL_AREAS.contains(baseArea)) {
            if (areaOrZone.equals(baseArea)) {
                log.warn("Malformed zonal series skipped. Expected zone (like: {}01), but found global zone: {}", baseArea, relativePath);
                return Optional.empty();
            }
            List<String> techTokens = trimTrailingYearTokens(remaining.subList(1, remaining.size()));
            if (techTokens.isEmpty()) return Optional.empty();
            return Optional.of(new ParsedSeriesKey(baseArea, normalizedGroup, clusterFolder, areaOrZone, String.join("_", techTokens)));
        }

        return Optional.of(new ParsedSeriesKey(areaOrZone, normalizedGroup, clusterFolder, null, null));
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

    private Map<String, ResSeriesRef> indexNonFrSeries(List<ResSeriesRef> series) {
        return series.stream()
                .filter(ref -> !ResDomainRules.FR_AREA.equalsIgnoreCase(ref.area()))
                .collect(Collectors.toMap(
                        ref -> ref.area().toUpperCase(Locale.ROOT) + "_" + ref.group().toUpperCase(Locale.ROOT) + "_" + ref.cluster().toUpperCase(Locale.ROOT),
                        ref -> ref,
                        this::mergeSeriesRefs
                ));
    }

    /**
     * Priority: specific area > OTHERS; area-techno > area-level.
     * - specific+techno=3, specific+area=2, others+techno=1, others+area=0.
     * If priority is equal, ther's a conflict
     */
    private ResSeriesRef mergeSeriesRefs(ResSeriesRef existing, ResSeriesRef replacement) {
        int ep = seriesPriority(existing);
        int rp = seriesPriority(replacement);
        if (rp > ep) return replacement;
        if (ep > rp) return existing;
        throw BusinessException.builder()
                .message("Multiple load-factor series found for area '" + existing.area() + "', group '" + existing.group()
                        + "', cluster '" + existing.cluster()
                        + "'. Two trajectories linked to this area, group and cluster both contain a series for it:"
                        + " trajectory '" + existing.trajectoryFileName() + "' and trajectory '" + replacement.trajectoryFileName() + "'."
                        + " Only one trajectory should provide a load-factor series per area, RES group and cluster."
                        + " (conflicting file: '" + existing.sourceKey() + "')")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private int seriesPriority(ResSeriesRef ref) {
        return (ref.fromOthersArea() ? 0 : 2) + (ref.fromTechnoTrajectory() ? 1 : 0);
    }

    private Map<String, ResSeriesRef> indexFrSeries(List<ResSeriesRef> series) {
        return series.stream()
                .filter(ref -> ResDomainRules.FR_AREA.equalsIgnoreCase(ref.area()))
                .collect(Collectors.toMap(
                        ref -> ref.zone().toUpperCase(Locale.ROOT) + "_" + ref.group().toUpperCase(Locale.ROOT) + "_" + ref.cluster().toUpperCase(Locale.ROOT) + "_" + ref.technology().toUpperCase(Locale.ROOT),
                        ref -> ref,
                        this::mergeSeriesRefs
                ));
    }

    private TrajectoryCollections collectTrajectoriesSinglePass(StudyEntity studyEntity) {
        var areaCapacities = new LinkedHashMap<String, List<ResClusterCapacityEntity>>();
        var technoCapacities = new LinkedHashMap<String, List<ResClusterCapacityEntity>>();
        var technologies = new LinkedHashMap<String, List<ResTechnologyDistributionEntity>>();
        var zonals = new LinkedHashMap<String, List<ResZonalDistributionEntity>>();

        studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .forEach(trajectory -> routeTrajectoryData(trajectory, areaCapacities, technoCapacities, technologies, zonals));

        return new TrajectoryCollections(mergeCapacities(areaCapacities, technoCapacities), technologies, zonals);
    }

    private void routeTrajectoryData(
            TrajectoryEntity trajectory,
            Map<String, List<ResClusterCapacityEntity>> areaCapacities,
            Map<String, List<ResClusterCapacityEntity>> technoCapacities,
            Map<String, List<ResTechnologyDistributionEntity>> technologies,
            Map<String, List<ResZonalDistributionEntity>> zonals
    ) {
        var type = TrajectoryType.valueOf(trajectory.getType());
        boolean isTechno = trajectory.getTechnology() != null && !trajectory.getTechnology().isBlank();

        switch (type) {
            case RES_CAPACITY -> extractCapacities(trajectory, isTechno ? technoCapacities : areaCapacities);
            case RES_TECHNOLOGY_DISTRIBUTION -> extractTechnologies(trajectory, technologies);
            case RES_ZONAL_DISTRIBUTION -> extractZonals(trajectory, zonals);
            default -> { /* ignore */ }
        }
    }

    /**
     * Produces the final capacity map with area-techno and area logic:
     *   Start with area-techno capacities
     *   Then for each area that also has area capacity, add only the groups not already in area-techno
     * @param areaOnly
     * @param technoSpecific
     * @return the final capacity entities for each area
     */
    private Map<String, List<ResClusterCapacityEntity>> mergeCapacities(
            Map<String, List<ResClusterCapacityEntity>> areaOnly,
            Map<String, List<ResClusterCapacityEntity>> technoSpecific
    ) {
        if (technoSpecific.isEmpty()) return areaOnly;
        if (areaOnly.isEmpty()) return technoSpecific;

        var merged = new LinkedHashMap<>(technoSpecific);

        areaOnly.forEach((area, areaEntities) -> {
            if (!merged.containsKey(area)) {
                merged.put(area, areaEntities);
            } else {
                var technoGroups = merged.get(area).stream()
                        .map(e -> normalizeGroup(e.getGroupe()))
                        .collect(Collectors.toSet());

                var combined = new ArrayList<>(merged.get(area));
                areaEntities.stream()
                        .filter(e -> !technoGroups.contains(normalizeGroup(e.getGroupe())))
                        .forEach(combined::add);
                merged.put(area, combined);
            }
        });

        return merged;
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
