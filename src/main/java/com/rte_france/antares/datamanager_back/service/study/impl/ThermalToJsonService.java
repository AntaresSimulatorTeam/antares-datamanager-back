package com.rte_france.antares.datamanager_back.service.study.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.ThermalClusterGenerationDto;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalPropertiesAssemblerService;
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
    private static final String MATRIX_HASH = "matrix hash";


    public  Map<String, ThermalClusterGenerationDto> getClusterPropsForArea(Map<ThermalPropertiesAssemblerService.AreaClusterRefKey, ThermalClusterGenerationDto> areaRefProps, String areaName) {
        return areaRefProps.entrySet().stream()
                .filter(e -> e.getKey().area().equalsIgnoreCase(areaName))
                .collect(Collectors.toMap(
                        e -> e.getKey().area().toUpperCase(Locale.ROOT) + "_" + e.getKey().thermalClusterRef().getName(),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    public Map<String, Object> thermalsMapGenerator(Map<String, ThermalClusterGenerationDto> clusterProps) {
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
            clusterData.put("series", MATRIX_HASH);
            clusterData.put("fuel_cost", MATRIX_HASH);
            clusterData.put("co2_cost", MATRIX_HASH);
            clusterData.put(DATA, dataMap);
            clusterData.put("modulation", dto.getParamModulationTsList());

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

    private static final ObjectMapper PARAM_MODULATION_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(ThermalClusterGenerationDto.ThermalClusterViews.ParamModulation.class));

}
