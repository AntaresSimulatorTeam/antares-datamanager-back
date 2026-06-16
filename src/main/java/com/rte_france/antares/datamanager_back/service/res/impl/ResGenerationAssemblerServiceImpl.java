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

    private final NasFileService nasFileService;
    private final AntaresDataManagerProperties antaresDataManagerProperties;
    private final PathSecurityUtil pathSecurityUtil;

    private record TrajectoryCollections(
            Map<String, List<ResClusterCapacityEntity>> capacities,
            Map<String, List<ResTechnologyDistributionEntity>> technologies,
            Map<String, List<ResZonalDistributionEntity>> zonals
    ) {}

    private record ClusterAggregationContext(
            String area,
            List<ResTechnologyDistributionEntity> techDistributions,
            List<ResZonalDistributionEntity> zonalDistributions,
            Map<String, ResSeriesRef> frSeriesIndex,
            Map<String, ResSeriesRef> nonFrSeriesIndex
    ) {}

    private record ResSeriesRef(String trajectoryFileName, String sourceKey, String arrowPath, String area, String group, String cluster, String zone, String technology, boolean fromTechnoTrajectory, boolean fromOthersArea) { }

    private record SeriesScanContext(String trajectoryFileName, boolean fromTechnoTrajectory, boolean fromOthersArea, String linkedArea, String linkedTech) { }

    private record ParsedSeriesKey(String area, String group, String cluster, String zone, String technology) { }

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

        var series = createArrowSeriesForResLoad(studyEntity);
        var frSeriesIndex = indexFrSeries(series);
        var nonFrSeriesIndex = indexNonFrSeries(series);

        return processAreaClusters(capacities, collections, frSeriesIndex, nonFrSeriesIndex);
    }

    private String resolveIndexedSingleSeries(String area, String group, String cluster, Map<String, ResSeriesRef> nonFrSeriesIndex) {
        var lookupKey = area.toUpperCase(Locale.ROOT) + "_" + group.toUpperCase(Locale.ROOT) + "_" + cluster.toUpperCase(Locale.ROOT);
        var match = nonFrSeriesIndex.get(lookupKey);

        if (match != null) {
            return match.arrowPath();
        }

        throw BusinessException.builder()
                .message("No load-factor series found for area '" + area + "', group '" + group
                        + "', cluster '" + cluster + "'. Link a load-factor trajectory that contains a series file for this area and group.")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private Map<String, Map<String, ResClusterGenerationDto>> processAreaClusters(
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

    private Map<String, ResClusterGenerationDto> buildClustersForArea(List<ResClusterCapacityEntity> capacities, ClusterAggregationContext context) {
        return capacities.stream()
                .collect(Collectors.groupingBy(ResClusterCapacityEntity::getCluster, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> buildClusterEntry(entry.getKey(), entry.getValue(), context),
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
                    groupKey, clusterName, installedPower, context.techDistributions(), context.zonalDistributions(), context.frSeriesIndex()
            );
            return new ResClusterGenerationDto(clusterProperties, Collections.emptyList(), frAggregation);
        } else {
            var seriesPath = resolveIndexedSingleSeries(context.area(), groupKey, clusterName, context.nonFrSeriesIndex());
            return new ResClusterGenerationDto(clusterProperties, List.of(seriesPath), null);
        }
    }

    private ResFrAggregationDto buildFrAggregation(
            String normalizedGroup,
            String cluster,
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
                .filter(entity -> cluster.equalsIgnoreCase(entity.getCluster()))
                .forEach(entity -> processTechnologyEntity(
                        entity, normalizedGroup, cluster, zoneWeights, techWeightsByZone, seriesByZoneAndTech, frSeriesIndex
                ));

        validateFrAggregation(normalizedGroup, cluster, installedPower, zoneWeights, techWeightsByZone, seriesByZoneAndTech);

        return new ResFrAggregationDto(zoneWeights, techWeightsByZone, seriesByZoneAndTech);
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
            String cluster,
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

        var arrowPath = resolveIndexedFrSeries(zone, normalizedGroup, cluster, technology, frSeriesIndex);
        seriesByZoneAndTech.computeIfAbsent(zone, k -> new LinkedHashMap<>()).put(technology, arrowPath);
    }

    private String resolveIndexedFrSeries(
            String zone,
            String group,
            String cluster,
            String technology,
            Map<String, ResSeriesRef> frSeriesIndex
    ) {
        var candidateKeys = buildFrTechnologyCandidateKeys(group, technology);

        for (var candidateKey : candidateKeys) {
            var lookupKey = zone.toUpperCase(Locale.ROOT) + "_" + group.toUpperCase(Locale.ROOT) + "_"
                    + cluster.toUpperCase(Locale.ROOT) + "_" + candidateKey.toUpperCase(Locale.ROOT);
            var match = frSeriesIndex.get(lookupKey);
            if (match != null) {
                return match.arrowPath();
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
            Map<String, Double> zoneWeights,
            Map<String, Map<String, Double>> techWeightsByZone,
            Map<String, Map<String, String>> seriesByZoneAndTech
    ) {
        if (installedPower <= 0d) {
            return;
        }

        if (zoneWeights.isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing FR aggregation data for RES group " + normalizedGroup + ", cluster " + cluster)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        zoneWeights.forEach((zone, weight) ->
                validateZoneAggregation(normalizedGroup, cluster, zone, weight, techWeightsByZone, seriesByZoneAndTech));
    }

    private void validateZoneAggregation(
            String normalizedGroup,
            String cluster,
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
                    log.debug("Missing FR technology rows for active zone {} in RES group {}, cluster {}", zone, normalizedGroup, cluster);
                }
                throw BusinessException.builder()
                        .message("Missing FR technology mapping for zone " + zone + IN_RES_GROUP_SUFFIX + normalizedGroup + ", cluster " + cluster)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            if (log.isDebugEnabled()) {
                Map<String, String> techSeries = seriesByZoneAndTech.get(zone);
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
            if (scanContext.fromOthersArea() && ResDomainRules.FR_AREA.equalsIgnoreCase(parsedKey.area())) return;
            if (!scanContext.fromOthersArea() && scanContext.linkedArea() != null && !scanContext.linkedArea().isBlank() && !scanContext.linkedArea().equalsIgnoreCase(parsedKey.area())) {
                return;
            }
            if (scanContext.linkedTech() != null && !scanContext.linkedTech().isBlank() && !toKey(scanContext.linkedTech()).equalsIgnoreCase(parsedKey.group())) {
                return;
            }
            String outputDir = antaresDataManagerProperties.getResTsOutputDirectory();
            try {
                String arrowName = nasFileService.readAndSaveMatrixToNas(file, outputDir, null, true);
                result.add(new ResSeriesRef(
                        scanContext.trajectoryFileName(),
                        toKey(rel.replace('\\', '/')),
                        arrowName,
                        parsedKey.area(),
                        parsedKey.group(),
                        parsedKey.cluster(),
                        parsedKey.zone(),
                        parsedKey.technology(),
                        scanContext.fromTechnoTrajectory(),
                        scanContext.fromOthersArea()
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
                log.warn("Malformed zonal series skipped (area without zonal identifier, expected example: {}01): {}", baseArea, relativePath);
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
