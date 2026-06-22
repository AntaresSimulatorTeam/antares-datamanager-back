package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.HydroPropertiesGenerationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class HydroToJsonService {
    private static final String PROPERTIES = "properties";
    private static final String SERIES = "series";
    private static final String ALLOCATION = "allocation";
    private static final String PSP = "psp";

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

        String[] series = areaHydro.stream()
                .map(HydroGenerationDTO::getSeries)
                .filter(s -> s != null && s.length > 0)
                .findFirst()
                .orElse(new String[0]);

        boolean isPsp = Arrays.stream(series).anyMatch(s -> s.contains("_psp"));

        Map<String, Double> allocation = areaHydro.stream()
                .map(HydroGenerationDTO::getAllocation)
                .filter(a -> a != null && !a.isEmpty())
                .findFirst()
                .orElse(Collections.emptyMap());

        List<HydroPropertiesGenerationDTO> areaHydroWithoutMetadata = areaHydro.stream()
                .map(HydroGenerationDTO::getProperties)
                .toList();

        Map<String, Object> areaHydroMap = new LinkedHashMap<>();
        areaHydroMap.put(PROPERTIES, areaHydroWithoutMetadata);
        areaHydroMap.put(SERIES, series);
        areaHydroMap.put(ALLOCATION, allocation);
        areaHydroMap.put(PSP, isPsp);

        return areaHydroMap;
    }
}

