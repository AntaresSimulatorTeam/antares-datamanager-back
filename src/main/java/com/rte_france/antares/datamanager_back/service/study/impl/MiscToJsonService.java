package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MiscToJsonService {

    private static final String PROPERTIES = "properties";

    public Map<String, Object> buildMiscDataMap(String areaName, Map<String, List<MiscGenerationDTO>> miscProps) {
        if (miscProps == null || miscProps.isEmpty()) {
            log.info("miscMapGenerator: missing misc for area={}", areaName);
            return Collections.emptyMap();
        }

        List<MiscGenerationDTO> areaDtos = miscProps.get(areaName.toUpperCase());
        if (areaDtos == null || areaDtos.isEmpty()) {
            log.info("miscMapGenerator: no MISC found for area={}", areaName);
            return Collections.emptyMap();
        }

        Map<String, Object> miscDataMap = new LinkedHashMap<>();

        for (MiscGenerationDTO dto : areaDtos) {
            String group = dto.getGroupe();
            if (group == null) continue;

            Map<String, Object> propertiesMap = new LinkedHashMap<>();
            propertiesMap.put("capacity", dto.getCapacity());
            propertiesMap.put("group", group);

            Map<String, Object> groupData = new LinkedHashMap<>();
            groupData.put(PROPERTIES, propertiesMap);

            // Filter series for this group
            List<String> series = dto.getMiscGenTsList().stream()
                    .filter(fileName -> fileName.toUpperCase().contains("_" + group.toUpperCase() + "."))
                    .collect(Collectors.toList());

            groupData.put("series", series);

            miscDataMap.put(group, groupData);
            log.info("MISC group added {} for area {}", group, areaName);
        }

        log.info("miscMapGenerator: {} MISC added for area {}", miscDataMap.size(), areaName);
        return miscDataMap;
    }
}
