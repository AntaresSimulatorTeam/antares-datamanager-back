package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.ResClusterGenerationDto;
import com.rte_france.antares.datamanager_back.util.AreaNaming.AreaKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
public class ResToJsonService {

    public Map<String, ResClusterGenerationDto> buildResDataMap(String areaName, Map<AreaKey, Map<String, ResClusterGenerationDto>> resPropsByArea) {
        if (resPropsByArea == null || resPropsByArea.isEmpty()) {
            log.info("resMapGenerator: missing RES data for area={}", areaName);
            return Collections.emptyMap();
        }

        if (areaName == null || areaName.isBlank()) {
            log.info("resMapGenerator: missing area name");
            return Collections.emptyMap();
        }

        Map<String, ResClusterGenerationDto> areaRes = resPropsByArea.get(AreaKey.of(areaName));
        if (areaRes == null || areaRes.isEmpty()) {
            log.info("resMapGenerator: no RES found for area={}", areaName);
            return Collections.emptyMap();
        }

        return areaRes;
    }
}
