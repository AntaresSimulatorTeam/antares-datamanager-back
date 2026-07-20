package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.HydroAreaGenerationDTO;
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

    public Map<String, Object> buildHydroDataMap(String areaName, Map<String, HydroAreaGenerationDTO> hydroPropsByArea) {
        if (hydroPropsByArea == null || hydroPropsByArea.isEmpty()) {
            log.info("hydroMapGenerator: missing Hydro data for area={}", areaName);
            return Collections.emptyMap();
        }

        if (areaName == null || areaName.isBlank()) {
            log.info("hydroMapGenerator: missing area name");
            return Collections.emptyMap();
        }

        HydroAreaGenerationDTO areaHydro = hydroPropsByArea.get(areaName.toUpperCase());
        if (areaHydro == null || (areaHydro.hydro() == null && areaHydro.psp() == null)) {
            log.info("hydroMapGenerator: no Hydro found for area={}", areaName);
            return Collections.emptyMap();
        }

        Map<String, Object> areaHydroMap = new LinkedHashMap<>();
        areaHydroMap.put(PROPERTIES, extractProperties(areaHydro.hydro()));
        areaHydroMap.put(SERIES, extractSeries(areaHydro.hydro()));
        areaHydroMap.put(ALLOCATION, extractAllocation(areaHydro.hydro()));
        areaHydroMap.put(PSP, buildPspSubMap(areaHydro.psp()));

        return areaHydroMap;
    }

    private HydroPropertiesGenerationDTO extractProperties(HydroGenerationDTO dto) {
        return dto != null ? dto.getProperties() : null;
    }

    private String[] extractSeries(HydroGenerationDTO dto) {
        if (dto == null || dto.getSeries() == null || dto.getSeries().length == 0) return new String[0];
        return dto.getSeries();
    }

    private Map<String, Double> extractAllocation(HydroGenerationDTO dto) {
        if (dto == null || dto.getAllocation() == null || dto.getAllocation().isEmpty()) return Collections.emptyMap();
        return dto.getAllocation();
    }

    private Map<String, Object> buildPspSubMap(HydroGenerationDTO pspDto) {
        if (pspDto == null) return Collections.emptyMap();
        Map<String, Object> pspMap = new LinkedHashMap<>();
        pspMap.put(PROPERTIES, pspDto.getProperties());
        pspMap.put(SERIES, pspDto.getSeries() != null ? pspDto.getSeries() : new String[0]);
        pspMap.put(ALLOCATION, pspDto.getAllocation() != null && !pspDto.getAllocation().isEmpty()
                ? pspDto.getAllocation() : Collections.emptyMap());
        return pspMap;
    }
}

