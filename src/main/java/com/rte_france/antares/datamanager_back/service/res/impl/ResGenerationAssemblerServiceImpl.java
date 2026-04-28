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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResGenerationAssemblerServiceImpl implements ResGenerationAssemblerService {

    private static final String FR_AREA = "FR";
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
        if (studyEntity.getTrajectories() == null || studyEntity.getTrajectories().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<ResClusterCapacityEntity>> capacitiesByArea = collectCapacitiesByArea(studyEntity);
        if (capacitiesByArea.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<ResTechnologyDistributionEntity>> technologyByArea = collectTechnologyByArea(studyEntity);
        Map<String, List<ResZonalDistributionEntity>> zonalByArea = collectZonalByArea(studyEntity);
        List<ResSeriesRef> generatedSeries = createArrowSeriesForResLoad(studyEntity);

        Map<String, Map<String, Object>> resByArea = new LinkedHashMap<>();
        capacitiesByArea.forEach((area, capacities) -> {
            Map<String, Object> clusters = buildClustersForArea(
                    area,
                    capacities,
                    technologyByArea.getOrDefault(area, Collections.emptyList()),
                    zonalByArea.getOrDefault(area, Collections.emptyList()),
                    generatedSeries
            );
            resByArea.put(area, clusters);
        });

        return resByArea;
    }

    private Map<String, List<ResClusterCapacityEntity>> collectCapacitiesByArea(StudyEntity studyEntity) {
        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.RES_CAPACITY.name().equals(t.getType()))
                .map(TrajectoryEntity::getResClusterCapacityEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(e -> Boolean.TRUE.equals(e.getToUse()))
                .filter(e -> e.getArea() != null && e.getGroupe() != null && e.getCluster() != null)
                .collect(Collectors.groupingBy(e -> e.getArea().toUpperCase(Locale.ROOT), LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, List<ResTechnologyDistributionEntity>> collectTechnologyByArea(StudyEntity studyEntity) {
        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.RES_TECHNOLOGY_DISTRIBUTION.name().equals(t.getType()))
                .map(TrajectoryEntity::getResTechnologyDistributionCapacityEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(e -> e.getArea() != null && e.getGroupe() != null && e.getPecdZone() != null && e.getPecdTechnology() != null)
                .collect(Collectors.groupingBy(e -> e.getArea().toUpperCase(Locale.ROOT), LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, List<ResZonalDistributionEntity>> collectZonalByArea(StudyEntity studyEntity) {
        return studyEntity.getTrajectories().stream()
                .filter(Objects::nonNull)
                .filter(t -> TrajectoryType.RES_ZONAL_DISTRIBUTION.name().equals(t.getType()))
                .map(TrajectoryEntity::getResZonalDistributionCapacityEntities)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .filter(e -> e.getArea() != null && e.getGroupe() != null && e.getPecdZone() != null)
                .collect(Collectors.groupingBy(e -> e.getArea().toUpperCase(Locale.ROOT), LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, Object> buildClustersForArea(
            String area,
            List<ResClusterCapacityEntity> capacities,
            List<ResTechnologyDistributionEntity> technologyDistributions,
            List<ResZonalDistributionEntity> zonalDistributions,
            List<ResSeriesRef> generatedSeries
    ) {
        Map<String, List<ResClusterCapacityEntity>> byGroup = capacities.stream()
                .collect(Collectors.groupingBy(cap -> normalizeGroup(cap.getGroupe()), LinkedHashMap::new, Collectors.toList()));

        Map<String, Object> areaRes = new LinkedHashMap<>();
        byGroup.forEach((groupKey, entities) -> {
            double installedPower = entities.stream()
                    .map(ResClusterCapacityEntity::getCapacityByYear)
                    .filter(Objects::nonNull)
                    .mapToDouble(BigDecimal::doubleValue)
                    .sum();

            Map<String, Object> clusterPropertiesMap = new LinkedHashMap<>();
            clusterPropertiesMap.put(CAPACITY, installedPower);
            clusterPropertiesMap.put(GROUP, groupKey);

            Map<String, Object> clusterMap = new LinkedHashMap<>();
            clusterMap.put(PROPERTIES, clusterPropertiesMap);

            if (FR_AREA.equalsIgnoreCase(area)) {
                List<String> series = Collections.emptyList();
                Map<String, Object> frAggregation = buildFrAggregation(
                        groupKey,
                        installedPower,
                        technologyDistributions,
                        zonalDistributions,
                        generatedSeries
                );
                clusterMap.put(SERIES, series);
                clusterMap.put(FR_AGGREGATION, frAggregation);
            } else {
                String series = resolveSingleSeries(area, groupKey, generatedSeries);
                clusterMap.put(SERIES, List.of(series));
            }

            areaRes.put(groupKey, clusterMap);
        });

        return areaRes;
    }

    private Map<String, Object> buildFrAggregation(
            String normalizedGroup,
            double installedPower,
            List<ResTechnologyDistributionEntity> technologyDistributions,
            List<ResZonalDistributionEntity> zonalDistributions,
            List<ResSeriesRef> generatedSeries
    ) {
        Map<String, Double> zoneWeights = zonalDistributions.stream()
                .filter(e -> normalizedGroup.equals(normalizeGroup(e.getGroupe())))
                .collect(Collectors.toMap(
                        e -> canonicalFrZone(e.getPecdZone()),
                        e -> normalizeWeight(e.getCapacityByYear(), "zonal", e.getPecdZone(), e.getGroupe()),
                        Double::sum,
                        LinkedHashMap::new
                ));

        Map<String, Map<String, Double>> techWeightsByZone = new LinkedHashMap<>();
        Map<String, Map<String, String>> seriesByZoneAndTech = new LinkedHashMap<>();

        for (ResTechnologyDistributionEntity entity : technologyDistributions) {
            if (!normalizedGroup.equals(normalizeGroup(entity.getGroupe()))) {
                continue;
            }
            String zone = canonicalFrZone(entity.getPecdZone());
            String technology = toKey(entity.getPecdTechnology());
            double weight = normalizeWeight(entity.getCapacityByYear(), "technology", zone, entity.getPecdTechnology());

            techWeightsByZone.computeIfAbsent(zone, ignored -> new LinkedHashMap<>()).put(technology, weight);

            // Only resolve series if weight is > 0 (we skip technologies with no weight)
            if (weight > 0) {
                seriesByZoneAndTech.computeIfAbsent(zone, ignored -> new LinkedHashMap<>())
                        .put(technology, resolveFrSeries(zone, normalizedGroup, technology, generatedSeries));
            }
        }

        validateFrAggregation(normalizedGroup, installedPower, zoneWeights, techWeightsByZone, seriesByZoneAndTech);

        Map<String, Object> aggregation = new LinkedHashMap<>();
        aggregation.put(ZONE_WEIGHTS, zoneWeights);
        aggregation.put(TECH_WEIGHTS_BY_ZONE, techWeightsByZone);
        aggregation.put(SERIES_BY_ZONE_AND_TECH, seriesByZoneAndTech);
        return aggregation;
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

        if (zoneWeights.isEmpty() || techWeightsByZone.isEmpty() || seriesByZoneAndTech.isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing FR aggregation data for RES group " + normalizedGroup)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        double totalZoneWeight = zoneWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalZoneWeight < 0d) {
            throw BusinessException.builder()
                    .message("FR zone weights sum must be strictly positive for RES group " + normalizedGroup)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        for (String zone : zoneWeights.keySet()) {
            Map<String, Double> techWeights = techWeightsByZone.get(zone);
            Map<String, String> techSeries = seriesByZoneAndTech.get(zone);

            if (techWeights == null || techWeights.isEmpty()) {
                throw BusinessException.builder()
                        .message("Missing FR technology mapping for zone " + zone + IN_RES_GROUP_SUFFIX + normalizedGroup)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            // Verify that all technologies with weight > 0 have a series file
            for (Map.Entry<String, Double> entry : techWeights.entrySet()) {
                if (entry.getValue() > 0 && (techSeries == null || !techSeries.containsKey(entry.getKey()))) {
                    throw BusinessException.builder()
                            .message("Missing RES load factor series for technology " + entry.getKey() + " in zone " + zone + IN_RES_GROUP_SUFFIX + normalizedGroup)
                            .httpStatus(HttpStatus.BAD_REQUEST)
                            .build();
                }
            }

            double techSum = techWeights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (techSum < 0d) {
                throw BusinessException.builder()
                        .message("FR technology weights sum must be strictly positive for zone " + zone + IN_RES_GROUP_SUFFIX + normalizedGroup)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    private String resolveSingleSeries(String area, String group, List<ResSeriesRef> generatedSeries) {
        List<ResSeriesRef> fallbackCandidates = generatedSeries.stream()
                .filter(ref -> ref.matchesNonFr(area, group))
                .toList();

        if (fallbackCandidates.size() == 1) {
            return fallbackCandidates.getFirst().arrowPath();
        }

        throw BusinessException.builder()
                .message("Non-FR RES series must resolve to exactly one arrow for area/group " + area + "/" + group)
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();
    }

    private String resolveFrSeries(String zone, String group, String technology, List<ResSeriesRef> generatedSeries) {
        Set<String> candidateTechnologyKeys = buildFrTechnologyCandidateKeys(group, technology);

        List<ResSeriesRef> candidates = generatedSeries.stream()
                .filter(ref -> candidateTechnologyKeys.stream().anyMatch(candidate -> ref.matchesFr(zone, group, candidate)))
                .toList();

        if (candidates.size() != 1) {
            throw BusinessException.builder()
                    .message("FR RES series resolution must return exactly one arrow for zone " + zone + " and technology " + technology)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return candidates.getFirst().arrowPath();
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
        for (TrajectoryEntity trajectory : resLoadTrajectories) {
            String trajectoryFileName = trajectory.getFileName();
            if (trajectoryFileName == null || trajectoryFileName.isBlank()) {
                continue;
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
                    walk.filter(Files::isRegularFile)
                            .filter(file -> !isInOldSubdirectory(trajectoryRoot, file))
                            .filter(this::isSupportedSeriesFormat)
                            .forEach(file -> {
                                String rel = trajectoryRoot.relativize(file).toString();
                                Optional<ParsedSeriesKey> parsedSeriesKey = parseSeriesKeyFromRelativePath(rel);
                                if (parsedSeriesKey.isEmpty()) {
                                    return;
                                }
                                String outputDir = antaresDataManagerProperties.getOutputLoadDirectory();
                                try {
                                    String arrowName = nasFileService.saveMatrixToNas(file, outputDir);
                                    result.add(new ResSeriesRef(
                                            toKey(rel.replace('\\', '/')),
                                            arrowName,
                                            parsedSeriesKey.get().area(),
                                            parsedSeriesKey.get().group(),
                                            parsedSeriesKey.get().zone(),
                                            parsedSeriesKey.get().technology()
                                    ));
                                } catch (IOException e) {
                                    throw TechnicalException.builder()
                                            .message("Could not generate RES arrow file from " + file)
                                            .cause(e)
                                            .build();
                                }
                            });
                }
            } catch (IOException e) {
                throw TechnicalException.builder()
                        .message("Could not list RES load trajectory files for " + trajectoryFileName)
                        .cause(e)
                        .build();
            }
        }

        return result;
    }

    private boolean isInOldSubdirectory(Path trajectoryRoot, Path file) {
        Path relative = trajectoryRoot.relativize(file);
        for (Path part : relative) {
            if ("old".equalsIgnoreCase(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupportedSeriesFormat(Path file) {
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean hasSupportedExtension = lowerName.endsWith(".csv") || lowerName.endsWith(".txt") || lowerName.endsWith(".xlsx");
        if (!hasSupportedExtension || lowerName.startsWith(".~lock.")) {
            return false;
        }
        return true;
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
        if (isFrZone(areaOrZone)) {
            String zone = areaOrZone.toUpperCase(Locale.ROOT);
            List<String> technologyTokens = trimTrailingYearTokens(tokens.subList(technologyStartIndex, tokens.size()));
            if (technologyTokens.isEmpty()) {
                return Optional.empty();
            }
            String technology = String.join("_", technologyTokens);
            return Optional.of(new ParsedSeriesKey(FR_AREA, normalizedGroup, zone, technology));
        }

        String area = areaOrZone.toUpperCase(Locale.ROOT);
        return Optional.of(new ParsedSeriesKey(area, normalizedGroup, null, null));
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

    private boolean isFrZone(String token) {
        return token != null && token.matches("(?i)fr\\d+");
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

    private record ResSeriesRef(String sourceKey, String arrowPath, String area, String group, String zone, String technology) {
        boolean matchesNonFr(String expectedArea, String expectedGroup) {
            return !FR_AREA.equalsIgnoreCase(area)
                    && area.equalsIgnoreCase(expectedArea)
                    && group.equalsIgnoreCase(expectedGroup);
        }

        boolean matchesFr(String expectedZone, String expectedGroup, String expectedTechnology) {
            return FR_AREA.equalsIgnoreCase(area)
                    && zone != null
                    && zone.equalsIgnoreCase(expectedZone)
                    && group.equalsIgnoreCase(expectedGroup)
                    && technology != null
                    && technology.equalsIgnoreCase(expectedTechnology);
        }
    }

    private record ParsedSeriesKey(String area, String group, String zone, String technology) {
    }
}
