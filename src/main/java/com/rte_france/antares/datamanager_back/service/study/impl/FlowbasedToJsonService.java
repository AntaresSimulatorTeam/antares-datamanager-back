package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FlowbasedLinkCapacityType;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowbasedToJsonService {

    private final FlowbasedVirtualNodesRepository flowbasedVirtualNodesRepository;
    private final FlowbasedTypeDaysRepository flowbasedTypeDaysRepository;
    private final FlowbasedLinkCapacityRepository flowbasedLinkCapacityRepository;
    private final AntaresDataManagerProperties antaresDataManagerProperties;

    /**
     * Builds flowbased JSON from database entities by trajectory ID
     * @param trajectory the Flowbased trajectory
     * @return Map containing flowbased with structure: {recalculate_ts, type_days, virtual_nodes, links}
     */
    public Map<String, Object> buildFlowbasedMap(TrajectoryEntity trajectory, boolean recalculate) {
        Integer trajectoryId = trajectory.getId();
        Map<String, Object> flowbased = new LinkedHashMap<>();

        List<FlowbasedTypeDayEntity> dayTypesParams = flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(trajectoryId);
        List<FlowbasedVirtualNodesEntity> virtualNodesParams = flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(trajectoryId);
        List<FlowbasedLinkCapacityEntity> linksParams = flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(trajectoryId);
        
        flowbased.put("recalculate_ts", recalculate);
        
        if (recalculate && !dayTypesParams.isEmpty()) {
            flowbased.put("type_days", buildTypeDaysMap(dayTypesParams));
        }

        if (!virtualNodesParams.isEmpty()) {
            flowbased.put("virtual_nodes", buildVirtualNodes(virtualNodesParams));
        } 
        
        if (!linksParams.isEmpty()) {
            flowbased.put("links", buildLinksMap(linksParams));
        }
        
        flowbased.put("ts_path", getFlowbasedTrajectoryPath(trajectory.getFileName()));

        return flowbased;
    }

    private List<Map<String,Object>> buildTypeDaysMap(List<FlowbasedTypeDayEntity> entities) {
        return entities.stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    putIfNotNull(map, "clustering", item.getClustering());
                    putIfNotNull(map, "id_type_day", item.getIdTypeDay());
                    putIfNotNull(map, "class_day", item.getClassDay());
                    return map;
                })
                .toList();
    }
    

    private List<String> buildVirtualNodes(List<FlowbasedVirtualNodesEntity> entities) {
        return entities.stream()
                .map(FlowbasedVirtualNodesEntity::getName)
                .toList();
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private List<Map<String, Object>> buildLinksMap(List<FlowbasedLinkCapacityEntity> entities) {
        return entities.stream()
                .map(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", item.getName());
                    if (item.getType() != FlowbasedLinkCapacityType.INFINITE) {
                        putIfNotNull(map, "winter_HP_direct_MW", item.getWinterHPDirectMW());
                        putIfNotNull(map, "winter_HP_indirect_MW", item.getWinterHPIndirectMW());
                        putIfNotNull(map, "winter_HC_direct_MW", item.getWinterHCDirectMW());
                        putIfNotNull(map, "winter_HC_indirect_MW", item.getWinterHCIndirectMW());
                        putIfNotNull(map, "summer_HP_direct_MW", item.getSummerHPDirectMW());
                        putIfNotNull(map, "summer_HP_indirect_MW", item.getSummerHPIndirectMW());
                        putIfNotNull(map, "summer_HC_direct_MW", item.getSummerHCDirectMW());
                        putIfNotNull(map, "summer_HC_indirect_MW", item.getSummerHCIndirectMW());
                    }
                    map.put("transmission_capacities", item.getType());
                    return map;
                })
                .toList();
    }
    
    private String getFlowbasedTrajectoryPath (String trajectoryToUse) {
        String[] parts = trajectoryToUse.split("###");
        
        return Paths.get(
                    antaresDataManagerProperties.getFlowbasedDirectory(),
                    parts[0],
                    parts[1]
            ).toString();
    }
}
