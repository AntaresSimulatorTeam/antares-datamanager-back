package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.SettingsAdvancedParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.SettingsSeedsParametersEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowbasedToJsonService {

    private final FlowbasedVirtualNodesRepository flowbasedVirtualNodesRepository;
    private final FlowbasedTypeDaysRepository flowbasedTypeDaysRepository;
    private final FlowbasedLinkCapacityRepository flowbasedLinkCapacityRepository;

    /**
     * Builds flowbased JSON from database entities by trajectory ID
     * @param trajectoryId the trajectory ID to fetch flowbased for
     * @return Map containing flowbased with structure: {recalculate_ts, type_days, virtual_nodes, links}
     */
    public Map<String, Object> buildFlowbasedMap(Integer trajectoryId, boolean recalculate) {
        Map<String, Object> flowbased = new LinkedHashMap<>();
        
        Optional<FlowbasedTypeDayEntity> dayTypesParams = flowbasedTypeDaysRepository.findByTrajectoryId(trajectoryId);
        Optional<FlowbasedVirtualNodesEntity> virtualNodesParams = flowbasedVirtualNodesRepository.findByTrajectoryId(trajectoryId);
        Optional<FlowbasedLinkCapacityEntity> linksParams = flowbasedLinkCapacityRepository.findByTrajectoryId(trajectoryId);
        
        flowbased.put("recalculate_ts", recalculate);
        
        if (recalculate && dayTypesParams.isPresent()) {
            flowbased.put("type_days", buildTypeDaysMap(dayTypesParams.get()));
        } else {
            flowbased.put("type_days", new LinkedHashMap<>());
        }

        if (virtualNodesParams.isPresent()) {
            flowbased.put("virtual_nodes", buildVirtualNodes(virtualNodesParams.get()));
        } else {
            flowbased.put("virtual_nodes", new LinkedHashMap<>());
        }

        if (linksParams.isPresent()) {
            flowbased.put("links", buildLinksMap(linksParams.get()));
        } else {
            flowbased.put("links", new LinkedHashMap<>());
        }

        return flowbased;
    }

    private Map<String, Object> buildTypeDaysMap(FlowbasedTypeDayEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        //addIfNotNull(map, "seed_tsgen_thermal", entity.getSeedTsgenThermal());
        return map;
    }

    private Map<String, Object> buildVirtualNodes(FlowbasedVirtualNodesEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        //addIfNotNull(map, "seed_tsgen_thermal", entity.getSeedTsgenThermal());
        return map;
    }

    private Map<String, Object> buildLinksMap(FlowbasedLinkCapacityEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        //addIfNotNull(map, "seed_tsgen_thermal", entity.getSeedTsgenThermal());
        return map;
    }

    private void addIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
