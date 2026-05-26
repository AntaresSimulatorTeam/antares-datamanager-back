package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.dto.MiscGenerationDTO;
import com.rte_france.antares.datamanager_back.repository.model.MiscGroupEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        Map<String, Double> capacityByGroup = new LinkedHashMap<>();
        Map<String, Set<String>> seriesByGroup = new LinkedHashMap<>();

        for (MiscGenerationDTO dto : areaDtos) {
            String group = MiscGroupEnum.normalizeForGenerator(dto.getGroupe());
            double capacity = dto.getCapacity() != null ? dto.getCapacity() : 0.0;

            capacityByGroup.merge(group, capacity, Double::sum);
            seriesByGroup.computeIfAbsent(group, ignored -> new LinkedHashSet<>());

            List<String> dtoSeries = dto.getMiscGenTsList();
            if (dtoSeries != null) {
                Set<String> matchingSeries = new LinkedHashSet<>();
                dtoSeries.stream()
                        .filter(fileName -> MiscGroupEnum.matchesSeriesForGroup(fileName, group))
                        .forEach(matchingSeries::add);

                seriesByGroup.get(group).addAll(matchingSeries);
            }
        }

        Map<String, Object> miscDataMap = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : capacityByGroup.entrySet()) {
            String group = entry.getKey();

            Map<String, Object> propertiesMap = new LinkedHashMap<>();
            propertiesMap.put("capacity", entry.getValue());
            propertiesMap.put("group", group);

            Map<String, Object> groupData = new LinkedHashMap<>();
            groupData.put(PROPERTIES, propertiesMap);
            groupData.put("series", List.copyOf(seriesByGroup.getOrDefault(group, Collections.emptySet())));

            miscDataMap.put(group, groupData);
        }

        log.info("miscMapGenerator: {} MISC added for area {}", miscDataMap.size(), areaName);
        return miscDataMap;
    }
}

