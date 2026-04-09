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

    private static final Set<String> VALID_FR_ZONES = buildFrZones();

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
        Set<String> expectedSeriesPrefixes = buildExpectedSeriesPrefixes(capacitiesByArea);

        Map<String, List<ResTechnologyDistributionEntity>> technologyByArea = collectTechnologyByArea(studyEntity);
        Map<String, List<ResZonalDistributionEntity>> zonalByArea = collectZonalByArea(studyEntity);
        List<ResSeriesRef> generatedSeries = createArrowSeriesForResLoad(studyEntity, expectedSeriesPrefixes);

        Map<String, Map<String, Object>> resByArea = new LinkedHashMap<>();
        capacitiesByArea.forEach((area, capacities) -> {
            Map<String, Object> clusters = buildClustersForArea(
                    area,
                    capacities,
                    technologyByArea.getOrDefault(area, Collections.emptyList()),
                    zonalByArea.getOrDefault(area, Collections.emptyList()),
                    generatedSeries
            );
            if (!clusters.isEmpty()) {
                resByArea.put(area, clusters);
            }
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

    private Set<String> buildExpectedSeriesPrefixes(Map<String, List<ResClusterCapacityEntity>> capacitiesByArea) {
        return capacitiesByArea.values().stream()
                .flatMap(Collection::stream)
                .map(ResClusterCapacityEntity::getGroupe)
                .filter(Objects::nonNull)
                .map(this::normalizeGroup)
                .map(this::prefixFromGroup)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
                        technologyDistributions,
                        zonalDistributions,
                        generatedSeries
                );
                validateFrContract(series, frAggregation, groupKey);
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
            seriesByZoneAndTech.computeIfAbsent(zone, ignored -> new LinkedHashMap<>())
                    .put(technology, resolveFrSeries(zone, normalizedGroup, technology, generatedSeries));
        }

        validateFrAggregation(normalizedGroup, zoneWeights, techWeightsByZone, seriesByZoneAndTech);

        Map<String, Object> aggregation = new LinkedHashMap<>();
        aggregation.put(ZONE_WEIGHTS, zoneWeights);
        aggregation.put(TECH_WEIGHTS_BY_ZONE, techWeightsByZone);
        aggregation.put(SERIES_BY_ZONE_AND_TECH, seriesByZoneAndTech);
        return aggregation;
    }

    private void validateFrContract(List<String> series, Map<String, Object> frAggregation, String clusterKey) {
        boolean hasSeries = series != null && !series.isEmpty();
        boolean hasAggregation = frAggregation != null && !frAggregation.isEmpty();
        if (hasSeries == hasAggregation) {
            throw BusinessException.builder()
                    .message("Invalid FR RES payload for cluster " + clusterKey + ": use either series or fr_aggregation, not both")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    private void validateFrAggregation(
            String normalizedGroup,
            Map<String, Double> zoneWeights,
            Map<String, Map<String, Double>> techWeightsByZone,
            Map<String, Map<String, String>> seriesByZoneAndTech
    ) {
        if (zoneWeights.isEmpty() || techWeightsByZone.isEmpty() || seriesByZoneAndTech.isEmpty()) {
            throw BusinessException.builder()
                    .message("Missing FR aggregation data for RES group " + normalizedGroup)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        double totalZoneWeight = zoneWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalZoneWeight <= 0d) {
            throw BusinessException.builder()
                    .message("FR zone weights sum must be strictly positive for RES group " + normalizedGroup)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        for (String zone : zoneWeights.keySet()) {
            Map<String, Double> techWeights = techWeightsByZone.get(zone);
            Map<String, String> techSeries = seriesByZoneAndTech.get(zone);

            if (techWeights == null || techWeights.isEmpty() || techSeries == null || techSeries.isEmpty()) {
                throw BusinessException.builder()
                        .message("Missing FR technology mapping for zone " + zone + IN_RES_GROUP_SUFFIX + normalizedGroup)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            Set<String> weightKeys = new LinkedHashSet<>(techWeights.keySet());
            Set<String> seriesKeys = new LinkedHashSet<>(techSeries.keySet());
            if (!weightKeys.equals(seriesKeys)) {
                throw BusinessException.builder()
                        .message("FR technology keys mismatch for zone " + zone + IN_RES_GROUP_SUFFIX + normalizedGroup)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }

            double techSum = techWeights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (techSum <= 0d) {
                throw BusinessException.builder()
                        .message("FR technology weights sum must be strictly positive for zone " + zone + IN_RES_GROUP_SUFFIX + normalizedGroup)
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build();
            }
        }
    }

    private String resolveSingleSeries(String area, String group, List<ResSeriesRef> generatedSeries) {
        String areaKey = toKey(area);
        String groupKey = toKey(group);

        List<ResSeriesRef> fallbackCandidates = generatedSeries.stream()
                .filter(ref -> ref.contains(areaKey))
                .filter(ref -> ref.contains(groupKey))
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
        String zoneKey = toKey(zone);
        String groupKey = toKey(group);
        String technologyKey = toKey(technology);

        List<ResSeriesRef> candidates = generatedSeries.stream()
                .filter(ref -> ref.contains(FR_AREA.toLowerCase(Locale.ROOT)))
                .filter(ref -> ref.contains(zoneKey))
                .filter(ref -> ref.contains(groupKey))
                .filter(ref -> ref.contains(technologyKey))
                .toList();

        if (candidates.size() != 1) {
            throw BusinessException.builder()
                    .message("FR RES series resolution must return exactly one arrow for zone " + zone + " and technology " + technology)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return candidates.getFirst().arrowPath();
    }

    private List<ResSeriesRef> createArrowSeriesForResLoad(StudyEntity studyEntity, Set<String> expectedSeriesPrefixes) {
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
                            .filter(file -> isSupportedSeriesFormat(file, expectedSeriesPrefixes))
                            .forEach(file -> {
                                String outputDir = antaresDataManagerProperties.getOutputLoadDirectory();
                                try {
                                    String arrowName = nasFileService.saveMatrixToNas(file, outputDir);
                                    String rel = trajectoryRoot.relativize(file).toString();
                                    String normalizedRel = toKey(rel.replace('\\', '/'));
                                    result.add(new ResSeriesRef(normalizedRel, arrowName));
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

    private String prefixFromGroup(String normalizedGroup) {
        String key = toKey(normalizedGroup);
        if (key.isBlank()) {
            throw BusinessException.builder()
                    .message("Invalid RES group value for series prefix")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }

        int separatorIndex = key.indexOf('_');
        String prefixRoot = separatorIndex > 0 ? key.substring(0, separatorIndex) : key;
        return prefixRoot + "_";
    }

    private boolean isSupportedSeriesFormat(Path file, Set<String> expectedSeriesPrefixes) {
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean hasSupportedExtension = lowerName.endsWith(".csv") || lowerName.endsWith(".txt") || lowerName.endsWith(".xlsx");
        if (!hasSupportedExtension || lowerName.startsWith(".~lock.")) {
            return false;
        }

        if (expectedSeriesPrefixes == null || expectedSeriesPrefixes.isEmpty()) {
            return true;
        }

        int extensionIndex = lowerName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? lowerName.substring(0, extensionIndex) : lowerName;
        String normalizedBaseName = toKey(baseName);
        return expectedSeriesPrefixes.stream().anyMatch(normalizedBaseName::startsWith);
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
        if (!VALID_FR_ZONES.contains(normalized)) {
            throw BusinessException.builder()
                    .message("Invalid FR zone key: " + zone)
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .build();
        }
        return normalized;
    }

    private static Set<String> buildFrZones() {
        Set<String> zones = new LinkedHashSet<>();
        for (int i = 1; i <= 26; i++) {
            zones.add("FR" + String.format("%02d", i));
        }
        return zones;
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
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replaceAll("\\s+", "_");
    }

    private record ResSeriesRef(String sourceKey, String arrowPath) {
        boolean contains(String token) {
            return sourceKey.contains(token);
        }
    }
}
