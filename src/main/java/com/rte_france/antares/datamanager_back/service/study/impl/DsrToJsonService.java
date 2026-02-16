package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.DsrGenerationDTO;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class DsrToJsonService {


    private static final String PROPERTIES = "properties";
    private static final String MATRIX_HASH = "matrix hash";


    public Map<String, Object> buildDsrDataMap(String areaName, Map<String, DsrGenerationDTO> dsrClusterProps) {
        if (dsrClusterProps == null || dsrClusterProps.isEmpty()) {
            log.info("dsrMapGenerator: missing dsrClusterProps for area={}", areaName);
            return Collections.emptyMap();
        }

        Map<String, Object> dsrClusterName = new LinkedHashMap<>();

        dsrClusterProps.entrySet().stream()
                .filter(e -> e.getKey().startsWith(areaName.toUpperCase() + "_"))
                .forEach(e -> {
                    String clusterName = e.getKey();
                    DsrGenerationDTO dto = e.getValue();
                    Map<String, Object> propertiesMap = getDsrPropertiesMap(dto);


                    Map<String, Object> clusterData = new LinkedHashMap<>();
                    clusterData.put(PROPERTIES, propertiesMap);
                    clusterData.put("series", dto.getDsrTsList());

                    dsrClusterName.put(clusterName, clusterData);
                    log.info("DSR cluster added {} for area {} (enabled={})", clusterName, areaName, dto.getEnabled());
                });

        log.info("dsrMapGenerator: {} DSR created for area {}", dsrClusterName.size(), areaName);
        return dsrClusterName;
    }

    private static @NonNull Map<String, Object> getDsrPropertiesMap(DsrGenerationDTO dto) {
        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("enabled", dto.getEnabled());
        propertiesMap.put("group", dto.getGroup());
        propertiesMap.put("nominal_capacity", dto.getNominalCapacity());
        propertiesMap.put("unit_count", dto.getUnitCount());
        propertiesMap.put("marginal_cost", dto.getMarginalCost());
        propertiesMap.put("market_bid_cost", dto.getMarketBidCost());
        return propertiesMap;
    }


}
