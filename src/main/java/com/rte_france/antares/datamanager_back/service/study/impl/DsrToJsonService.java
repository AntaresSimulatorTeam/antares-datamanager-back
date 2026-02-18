package com.rte_france.antares.datamanager_back.service.study.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class DsrToJsonService {


    private static final String PROPERTIES = "properties";
    private static final String DATA = "data";
    private static final String MODULATION = "modulation";


    public Map<String, Object> buildDsrDataMap(String areaName, Map<String, DsrGenerationDTO> dsrClusterProps) {
        if (dsrClusterProps == null || dsrClusterProps.isEmpty()) {
            log.info("dsrMapGenerator: missing dsrClusterProps for area={}", areaName);
            return Collections.emptyMap();
        }

        Map<String, Object> dsrClusterMap = new LinkedHashMap<>();

        dsrClusterProps.entrySet().stream()
                .filter(e -> e.getKey().startsWith(areaName.toUpperCase() + "_"))
                .forEach(e -> {
                    String clusterName = e.getKey();
                    DsrGenerationDTO dto = e.getValue();

                    Map<String, Object> propertiesMap = PROPERTIES_MAPPER.convertValue(dto, new TypeReference<>() {
                    });

                    Map<String, Object> dataMap = DATA_MAPPER.convertValue(dto, new TypeReference<>() {
                    });

                    Map<String, Object> modulationMap = MODULATION_MAPPER.convertValue(dto, new TypeReference<>() {
                    });

                    Map<String, Object> clusterData = new LinkedHashMap<>();
                    clusterData.put(PROPERTIES, propertiesMap);
                    clusterData.put(DATA, dataMap);
                    clusterData.put(MODULATION, modulationMap.get(MODULATION));

                    dsrClusterMap.put(clusterName, clusterData);
                    log.info("DSR cluster added {} for area {} (enabled={})", clusterName, areaName, dto.getEnabled());
                });

        log.info("dsrMapGenerator: {} DSR created for area {}", dsrClusterMap.size(), areaName);
        return dsrClusterMap;
    }

    private static final ObjectMapper PROPERTIES_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(DsrGenerationDTO.DsrClustersViews.Properties.class));

    private static final ObjectMapper DATA_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(DsrGenerationDTO.DsrClustersViews.Data.class));

    private static final ObjectMapper MODULATION_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(DsrGenerationDTO.DsrClustersViews.Modulation.class));

}
