package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FlowbasedLinkCapacityType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedConstraintsRestrictionEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkListEntity;
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
    private final FlowbasedConstraintsRestrictionsRepository flowbasedConstraintsRestrictionsRepository;
    private final FlowbasedLinkListRepository flowbasedLinkListRepository;
    private final AntaresDataManagerProperties antaresDataManagerProperties;

    /**
     * Builds flowbased JSON from database entities by trajectory ID
     * @param trajectory the Flowbased trajectory
     * @return Flowbased settings, including optional restriction_ahc_constraints.
     * Each restriction contains name, enabled, type, operator (stored as sign), links
     * resolved by list_links within the same trajectory, and a series path from second_member.
     */
    public Map<String, Object> buildFlowbasedMap(TrajectoryEntity trajectory, boolean recalculate) {
        Integer trajectoryId = trajectory.getId();
        Map<String, Object> flowbased = new LinkedHashMap<>();

        List<FlowbasedTypeDayEntity> dayTypesParams = flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(trajectoryId);
        List<FlowbasedVirtualNodesEntity> virtualNodesParams = flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(trajectoryId);
        List<FlowbasedLinkCapacityEntity> linksParams = flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(trajectoryId);
        List<FlowbasedConstraintsRestrictionEntity> restrictions =
                flowbasedConstraintsRestrictionsRepository.findEntitiesByTrajectoryId(trajectoryId);
        
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

        if (!restrictions.isEmpty()) {
            flowbased.put("restriction_ahc_constraints", buildRestrictions(restrictions, trajectory));
        }
        flowbased.put("ts_path", getFlowbasedTrajectoryPath(trajectory.getFileName()));
        return flowbased;
    }

    private String getFlowbasedTrajectoryPath(String trajectoryToUse) {
        String[] parts = trajectoryToUse.split("###");
        return Paths.get(antaresDataManagerProperties.getFlowbasedDirectory(), parts[0], parts[1]).toString();
    }

    private List<Map<String, Object>> buildRestrictions(
            List<FlowbasedConstraintsRestrictionEntity> restrictions, TrajectoryEntity trajectory) {
        Integer trajectoryId = trajectory.getId();
        Map<String, List<Map<String, Object>>> linkLists = new HashMap<>();
        return restrictions.stream()
                .map(restriction -> {
                    List<Map<String, Object>> links = linkLists.computeIfAbsent(restriction.getListLinks(), listName ->
                            flowbasedLinkListRepository.findEntitiesByTrajectoryIdAndListName(trajectoryId, listName)
                                    .stream().map(this::buildWeightedLink).toList());
                    if (links.isEmpty()) {
                        throw TechnicalException.builder()
                                .message("Missing or empty link list " + restriction.getListLinks()
                                        + " for flowbased restriction " + restriction.getName()
                                        + " in trajectory " + trajectoryId)
                                .build();
                    }
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("name", restriction.getName());
                    map.put("enabled", restriction.getEnabled());
                    putIfNotNull(map, "type", restriction.getType());
                    putIfNotNull(map, "operator", restriction.getSign());
                    map.put("links", links);
                    map.put("series", buildRestrictionSeriesPath(trajectory, restriction));
                    return map;
                })
                .toList();
    }

    private Map<String, Object> buildWeightedLink(FlowbasedLinkListEntity link) {
        Double weight = link.getPoids();
        if (weight == null || !Double.isFinite(weight)) {
            throw TechnicalException.builder()
                    .message("Invalid weight for link " + link.getLink()
                            + " in flowbased restriction list " + link.getListName()
                            + ": expected a finite, non-null poids")
                    .build();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", link.getLink());
        result.put("weight", weight);
        return result;
    }

    private String buildRestrictionSeriesPath(TrajectoryEntity trajectory,
                                               FlowbasedConstraintsRestrictionEntity restriction) {
        String horizon = trajectory.getHorizon();
        String fileName = trajectory.getFileName();
        String[] parts = fileName == null ? new String[0] : fileName.split("###", -1);
        String secondMember = restriction.getSecondMember();
        if (parts.length != 2 || !isPathSegment(parts[0])
                || horizon == null || !horizon.matches("[0-9]{4}(-[0-9]{4})?")
                || !isPathSegment(secondMember)) {
            throw TechnicalException.builder()
                    .message("Cannot build series path for flowbased restriction " + restriction.getName()
                            + " in trajectory " + trajectory.getId()
                            + ": expected a valid trajectory name, horizon YYYY or YYYY-YYYY,"
                            + " and a non-empty second_member filename without path separators")
                    .build();
        }
        int year = Arrays.stream(horizon.split("-")).mapToInt(Integer::parseInt).max().orElseThrow();
        return "/" + parts[0] + "/" + String.format(Locale.ROOT, "%04d", year)
                + "/restriction_ahc/ts/" + secondMember + ".txt";
    }

    private boolean isPathSegment(String value) {
        return value != null && !value.isBlank() && !value.equals(".") && !value.equals("..")
                && !value.contains("/") && !value.contains("\\") && value.indexOf('\0') < 0;
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
}
