package com.rte_france.antares.datamanager_back.service.study.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.NuclearSMRMixageDTO;
import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.dto.ThermalMEClusterGenerationDto;
import com.rte_france.antares.datamanager_back.service.thermal.AreaClusterRefKey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ThermalToJsonService {

    private static final String PROPERTIES = "properties";
    private static final String DATA = "data";
    private static final String MODULATION = "modulation";
    private static final String MATRIX_HASH = "matrix hash";

    public String buildClusterKey(String area, String clusterName) {
        return area.toUpperCase(Locale.ROOT) + "_" + clusterName;
    }

    public  Map<String, ThermalClusterGenerationDto> getClusterPropsForArea(Map<AreaClusterRefKey, ThermalClusterGenerationDto> areaRefProps, String areaName) {
        return areaRefProps.entrySet().stream()
                .filter(e -> e.getKey().area().equalsIgnoreCase(areaName))
                .collect(Collectors.toMap(
                        e -> buildClusterKey(e.getKey().area(), e.getKey().thermalClusterRef().getName()),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    public Map<String, Object> thermalsMapGenerator(Map<String, ThermalClusterGenerationDto> clusterProps) {
        return thermalsMapGenerator(clusterProps, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * @param seriesOverrides     final "series" value per cluster key, overriding the placeholder when present
     * @param smrMixageOverrides  SMR only metadata (active unit count + seed) when present
     */
    public Map<String, Object> thermalsMapGenerator(Map<String, ThermalClusterGenerationDto> clusterProps,
            Map<String, String> seriesOverrides,
            Map<String, NuclearSMRMixageDTO> smrMixageOverrides) {
        if (clusterProps == null || clusterProps.isEmpty()) {
            log.info("thermalsMapGenerator: missing clusterProps");
            return Collections.emptyMap();
        }

        Map<String, Object> clusterMap = new LinkedHashMap<>();

        clusterProps.forEach((clusterName, dto) -> {

            Map<String, Object> propertiesMap = PROPERTIES_MAPPER.convertValue(dto, new TypeReference<>() {
            });

            Map<String, Object> dataMap = DATA_MAPPER.convertValue(dto, new TypeReference<>() {
            });


            Map<String, Object> clusterData = new LinkedHashMap<>();
            clusterData.put(PROPERTIES, propertiesMap);
            clusterData.put("series", seriesOverrides.getOrDefault(clusterName, MATRIX_HASH));
            clusterData.put("fuel_cost", MATRIX_HASH);
            clusterData.put("co2_cost", MATRIX_HASH);
            clusterData.put(DATA, dataMap);
            clusterData.put("modulation", dto.getParamModulationTsList());

            NuclearSMRMixageDTO mixage = smrMixageOverrides.get(clusterName);
            if (mixage != null) {
                clusterData.put("smr_mixage", Map.of("unit_count", mixage.unitCount(), "seed", mixage.seed()));
            }

            clusterMap.put(clusterName, clusterData);
            log.info("Ajout thermal cluster {} avec {} propriétés", clusterName, propertiesMap.size());
        });

        log.info("thermalsMapGenerator: {} clusters générés", clusterMap.size());
        return clusterMap;
    }

    private static final ObjectMapper PROPERTIES_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(ThermalClusterGenerationDto.ThermalClusterViews.Properties.class));

    private static final ObjectMapper DATA_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(ThermalClusterGenerationDto.ThermalClusterViews.Data.class));

    private static final ObjectMapper PROPERTIES_MAPPER_ME = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(ThermalMEClusterGenerationDto.ThermalClusterViews.Properties.class));

    private static final ObjectMapper DATA_MAPPER_ME = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(ThermalMEClusterGenerationDto.ThermalClusterViews.Modulation.class));

    // ex: v_me_h2_long_euest : [v_me_h2_long_euest_import_canalisation]
    public Map<String, Object> thermalsMeMapGenerator(String areaName, Map<String, ThermalMEClusterGenerationDto> clusterProps) {
        if (clusterProps == null || clusterProps.isEmpty()) {
            log.info("thermalsMeMapGenerator: missing clusterProps for area ={}", areaName);
            return Collections.emptyMap();
        }

        Map<String, Object> thermalMeClusterName = new LinkedHashMap<>();

        clusterProps.entrySet().stream()
                .filter(e -> e.getKey().startsWith(areaName.toUpperCase() + "_"))
                .forEach(e -> {
                    String clusterName = e.getKey();
                    ThermalMEClusterGenerationDto dto = e.getValue();

                    Map<String, Object> propertiesMap = PROPERTIES_MAPPER_ME.convertValue(dto, new TypeReference<>() {});
                    Map<String, Object> dataMap = DATA_MAPPER_ME.convertValue(dto, new TypeReference<>() {
                    });

                    Map<String, Object> clusterData = new LinkedHashMap<>();
                    clusterData.put(PROPERTIES, propertiesMap);
                    clusterData.put(MODULATION, dataMap);

                    thermalMeClusterName.put(clusterName, clusterData);
                    log.info("Thermal ME cluster added {} for area {} (enabled={})", clusterName, areaName, dto.getEnabled());
                });

        log.info("thermalsMeMapGenerator: {} clusters Thermal ME added for area {}", thermalMeClusterName.size(), areaName);
        return thermalMeClusterName;
    }
}
