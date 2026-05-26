package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HydroToJsonService {
    private static final String PROPERTIES = "properties";

    public Map<String, Object> buildHydroDataMap(String areaName, Map<String, List<HydroGenerationDTO>> hydroPropsByArea) {
        if (hydroPropsByArea == null || hydroPropsByArea.isEmpty()) {
            log.info("hydroMapGenerator: missing Hydro data for area={}", areaName);
            return Collections.emptyMap();
        }

        if (areaName == null || areaName.isBlank()) {
            log.info("hydroMapGenerator: missing area name");
            return Collections.emptyMap();
        }

        List<HydroGenerationDTO> areaHydro = hydroPropsByArea.get(areaName.toUpperCase());
        if (areaHydro == null || areaHydro.isEmpty()) {
            log.info("hydroMapGenerator: no Hydro found for area={}", areaName);
            return Collections.emptyMap();
        }
        Map<String, Object> areaHydroMap = new LinkedHashMap<>();
        areaHydroMap.put(PROPERTIES, areaHydro);

        return areaHydroMap;
    }
}

