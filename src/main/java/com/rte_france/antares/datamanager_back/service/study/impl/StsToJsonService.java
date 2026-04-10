package com.rte_france.antares.datamanager_back.service.study.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StsToJsonService {

    private static final String PROPERTIES = "properties";
    private static final String SERIES_TS = "series";
    private static final String CONSTRAINT_PARAMETERS = "constraintParameters";
    private static final String CONSTRAINTS_SERIES_LIST = "stsConstraintsSeriesList";


    public Map<String, Object> stsMapGenerator(String areaName, Map<String, StsGenerationDTO> stsClusterProps) {
        if (stsClusterProps == null || stsClusterProps.isEmpty()) {
            log.info("stsMapGenerator: missing stsClusterProps for area ={}", areaName);
            return Collections.emptyMap();
        }

        Map<String, Object> stsClusterName = new LinkedHashMap<>();

        stsClusterProps.entrySet().stream()
                .filter(e -> e.getKey().startsWith(areaName.toUpperCase() + "_"))
                .forEach(e -> {
                    String clusterName = e.getKey();
                    StsGenerationDTO dto = e.getValue();

                    Map<String, Object> propertiesMap = PROPERTIES_MAPPER.convertValue(dto, new TypeReference<>() {});
                    Map<String, Object> seriesMap = SERIES_MAPPER.convertValue(dto, new TypeReference<>() {});
                    Map<String, Object> constraintsMap = buildConstraintsMap(dto);

                    Map<String, Object> clusterData = new LinkedHashMap<>();
                    clusterData.put(PROPERTIES, propertiesMap);
                    clusterData.put(SERIES_TS, seriesMap);
                    clusterData.putAll(constraintsMap);

                    stsClusterName.put(clusterName, clusterData);
                    log.info("STS cluster added {} for area {} (enabled={})", clusterName, areaName, dto.getEnabled());
                });

        log.info("stsMapGenerator: {} clusters STS added for area {}", stsClusterName.size(), areaName);
        return stsClusterName;
    }

    private Map<String, Object> buildConstraintsMap(StsGenerationDTO dto)

    {
        Map<String, Object> constraintsMap = new LinkedHashMap<>();

        Map<String, Object> rawConstraints = CONSTRAINTS_MAPPER.convertValue(dto, new TypeReference<>() {});

        Object parameters = rawConstraints.get(CONSTRAINT_PARAMETERS);
        if (parameters instanceof Map<?, ?> map && !map.isEmpty()) {
            constraintsMap.put(CONSTRAINT_PARAMETERS, parameters);
        }

        Object seriesList = rawConstraints.get(CONSTRAINTS_SERIES_LIST);
        if (seriesList instanceof List<?> list && !list.isEmpty()) {
            constraintsMap.put(CONSTRAINTS_SERIES_LIST, seriesList);
        }

        return constraintsMap;
    }

    private static final ObjectMapper PROPERTIES_MAPPER = new ObjectMapper()
            .disable(com.fasterxml.jackson.databind.MapperFeature.DEFAULT_VIEW_INCLUSION)
            .setConfig(new ObjectMapper().getSerializationConfig().withView(StsGenerationDTO.StsClustersViews.Properties.class));

    private static final ObjectMapper SERIES_MAPPER = new ObjectMapper()
            .disable(com.fasterxml.jackson.databind.MapperFeature.DEFAULT_VIEW_INCLUSION)
            .setConfig(new ObjectMapper().getSerializationConfig().withView(StsGenerationDTO.StsClustersViews.Series.class));

    private static final ObjectMapper CONSTRAINTS_MAPPER = new ObjectMapper()
            .setConfig(new ObjectMapper().getSerializationConfig().withView(StsGenerationDTO.StsClustersViews.Constraints.class));
}
