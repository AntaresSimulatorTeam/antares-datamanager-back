package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.ScenarioBuilderRepository;
import com.rte_france.antares.datamanager_back.repository.model.ScenarioBuilderEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScenarioBuilderToJsonService {

    private final ScenarioBuilderRepository scenarioBuilderRepository;

    /**
     * Builds scenario builder map grouped by category from database entities by trajectory ID
     * @param trajectoryId the trajectory ID to fetch scenario builder data for
     * @return Map containing scenario builder categories with lists of modulo strings
     */
    public Map<String, Object> buildScenarioBuilderMap(Integer trajectoryId) {
        if (trajectoryId == null) {
            log.warn("Trajectory ID is null for scenario builder");
            return Collections.emptyMap();
        }

        List<ScenarioBuilderEntity> entities = scenarioBuilderRepository.findByTrajectoryId(trajectoryId);
        if (entities == null || entities.isEmpty()) {
            log.info("No scenario builder data found for trajectory ID {}", trajectoryId);
            return Collections.emptyMap();
        }

        Map<String, List<String>> resultMap = new LinkedHashMap<>();
        for (ScenarioBuilderEntity entity : entities) {
            String category = entity.getCategory();
            String modulo = entity.getModulo();
            if (category != null && !category.isBlank() && modulo != null) {
                resultMap.computeIfAbsent(category, k -> new ArrayList<>()).add(modulo);
            }
        }

        log.info("Built scenario builder map with {} categories for trajectory ID {}", resultMap.size(), trajectoryId);
        return new LinkedHashMap<>(resultMap);
    }

}
