package com.rte_france.antares.datamanager_back.service.study;

import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StsToJson {

    private static final String PROPERTIES = "properties";
    public Map<String, Object> stsMapGenerator(String areaName, Map<String, StsGenerationDTO> stsClusterProps) {
        if (stsClusterProps == null || stsClusterProps.isEmpty()) {
            log.info("stsMapGenerator: pas de stsClusterProps pour area={}", areaName);
            return Collections.emptyMap();
        }

        Map<String, Object> stsClusterName = new LinkedHashMap<>();

        stsClusterProps.entrySet().stream()
                .filter(e -> e.getKey().startsWith(areaName.toUpperCase() + "_"))
                .forEach(e -> {
                    String clusterName = e.getKey();
                    StsGenerationDTO dto = e.getValue();
                    Map<String, Object> propertiesMap = new LinkedHashMap<>();
                    propertiesMap.put("enabled", dto.getEnabled());
                    propertiesMap.put("group", dto.getGroupe());
                    propertiesMap.put("injection_nominal_capacity", dto.getInjection());
                    propertiesMap.put("withdrawal_nominal_capacity", dto.getWithdrawal());
                    propertiesMap.put("reservoir_capacity", dto.getStorage());
                    propertiesMap.put("efficiency", dto.getEfficiencyInjection());
                    propertiesMap.put("efficiency_withdrawal", dto.getEfficiencyWithdrawal());
                    propertiesMap.put("initial_level", dto.getInitialLevel());
                    propertiesMap.put("initial_level_optim", dto.getInitialLevelOptim());

                    Map<String, Object> clusterData = new LinkedHashMap<>();
                    clusterData.put(PROPERTIES, propertiesMap);
                    clusterData.put("series", dto.getStsTsList());

                    stsClusterName.put(clusterName, clusterData);
                    log.info("Ajout STS cluster {} pour area {} (enabled={})", clusterName, areaName, dto.getEnabled());
                });

        log.info("stsMapGenerator: {} clusters STS générés pour area {}", stsClusterName.size(), areaName);
        return stsClusterName;
    }
}
