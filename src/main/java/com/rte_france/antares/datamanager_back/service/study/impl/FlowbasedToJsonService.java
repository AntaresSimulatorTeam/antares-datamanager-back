package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        List<FlowbasedTypeDayEntity> dayTypesParams = flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(trajectoryId);
        List<FlowbasedVirtualNodesEntity> virtualNodesParams = flowbasedVirtualNodesRepository.findByTrajectoryId(trajectoryId);
        List<FlowbasedLinkCapacityEntity> linksParams = flowbasedLinkCapacityRepository.findByTrajectoryId(trajectoryId);
        
        flowbased.put("recalculate_ts", recalculate);
        
        if (recalculate && !dayTypesParams.isEmpty()) {
            flowbased.put("type_days", linksParams); //buildTypeDaysMap(dayTypesParams));
        }

        if (!virtualNodesParams.isEmpty()) {
            flowbased.put("virtual_nodes", buildVirtualNodes(virtualNodesParams));
        } 
        
        if (!linksParams.isEmpty()) {
            flowbased.put("links", buildLinksMap(linksParams));
        }

        return flowbased;
    }

//    private List<Map<String,? extends Serializable>> buildTypeDaysMap(List<FlowbasedTypeDayEntity> entities) {
//        return entities.stream()
//                .map(item -> Map.of(
//                        "clustering", item.getClustering(),
//                        "id_type_day", item.getIdTypeDay(),
//                        "class_day", item.getClassDay()
//                ))
//                .toList();
//    }
    

    private List<String> buildVirtualNodes(List<FlowbasedVirtualNodesEntity> entities) {
        return entities.stream()
                .map(item -> item.getName().split("-")[0].trim())
                .toList();
    }

    private List<Map<String, String>> buildLinksMap(List<FlowbasedLinkCapacityEntity> entities) {
        return entities.stream()
                .map(item -> Map.of(
                        "name", item.getName().split("-")[0].trim()
                ))
                .toList();
    }
}
