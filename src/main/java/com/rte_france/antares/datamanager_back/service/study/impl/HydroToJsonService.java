package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HydroToJsonService {

    public Map<String, Object> buildHydroDataMap(String areaName, Map<String, List<HydroGenerationDTO>> hydroPropsByArea) {
        if (hydroPropsByArea == null || hydroPropsByArea.isEmpty()) {
            log.info("hydroMapGenerator: missing RES data for area={}", areaName);
            return Collections.emptyMap();
        }

        if (areaName == null || areaName.isBlank()) {
            log.info("resMapGenerator: missing area name");
            return Collections.emptyMap();
        }

        List<HydroGenerationDTO> areaHydro = hydroPropsByArea.get(areaName.toUpperCase());
        if (areaHydro == null || areaHydro.isEmpty()) {
            log.info("hydroMapGenerator: no RES found for area={}", areaName);
            return Collections.emptyMap();
        }

        return areaHydro;
    }
}

