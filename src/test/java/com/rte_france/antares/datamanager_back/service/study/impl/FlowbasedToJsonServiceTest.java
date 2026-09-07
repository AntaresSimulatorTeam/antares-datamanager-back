package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FlowbasedLinkCapacityType;
import com.rte_france.antares.datamanager_back.repository.FlowbasedLinkCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.FlowbasedTypeDaysRepository;
import com.rte_france.antares.datamanager_back.repository.FlowbasedVirtualNodesRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowbasedToJsonServiceTest {
    @Mock
    private FlowbasedVirtualNodesRepository flowbasedVirtualNodesRepository;
    @Mock
    private FlowbasedTypeDaysRepository flowbasedTypeDaysRepository;
    @Mock
    private FlowbasedLinkCapacityRepository flowbasedLinkCapacityRepository;
    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;
    @InjectMocks
    private FlowbasedToJsonService flowbasedToJsonService;

    @Test
    void shouldBuildCompleteFlowbasedMapWhenRecalculateIsTrue() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("study###trajectory.txt");

        FlowbasedTypeDayEntity typeDay = new FlowbasedTypeDayEntity();
        typeDay.setClustering("cluster");
        typeDay.setIdTypeDay(1);
        typeDay.setClassDay("WD");

        FlowbasedVirtualNodesEntity virtualNode = new FlowbasedVirtualNodesEntity();
        virtualNode.setName("FR - France");

        FlowbasedLinkCapacityEntity link = new FlowbasedLinkCapacityEntity();
        link.setName("FR-BE - Link");
        link.setWinterHPDirectMW(100);
        link.setWinterHPIndirectMW(90);
        link.setWinterHCDirectMW(80);
        link.setSummerHCIndirectMW(70);
        link.setSummerHPDirectMW(60);
        link.setSummerHPIndirectMW(50);
        link.setSummerHCDirectMW(40);
        link.setSummerHCIndirectMW(30);
        link.setType(FlowbasedLinkCapacityType.ENABLED);

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(typeDay));

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(virtualNode));

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(link));

        when(antaresDataManagerProperties.getFlowbasedDirectory())
                .thenReturn("/flowbased");

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, true);

        // Then
        assertEquals(true, result.get("recalculate_ts"));
        assertTrue(result.containsKey("type_days"));
        assertTrue(result.containsKey("virtual_nodes"));
        assertTrue(result.containsKey("links"));

        assertEquals(
                "/flowbased/study/trajectory.txt",
                result.get("ts_path")
        );

        List<Map<String, Object>> typeDays =
                (List<Map<String, Object>>) result.get("type_days");

        assertEquals("cluster", typeDays.get(0).get("clustering"));
        assertEquals(1, typeDays.get(0).get("id_type_day"));
        assertEquals("WD", typeDays.get(0).get("class_day"));

        List<String> virtualNodes =
                (List<String>) result.get("virtual_nodes");

        assertEquals(List.of("FR - France"), virtualNodes);

        List<Map<String, Object>> links =
                (List<Map<String, Object>>) result.get("links");

        assertEquals("FR-BE - Link", links.get(0).get("name"));
    }

    @Test
    void shouldAddCapacitiesForNonInfiniteLink() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("study###file");

        FlowbasedLinkCapacityEntity link = new FlowbasedLinkCapacityEntity();
        link.setName("FR-BE - Interco");
        link.setType(FlowbasedLinkCapacityType.ENABLED);
        link.setWinterHPDirectMW(100);
        link.setWinterHPIndirectMW(90);
        link.setWinterHCDirectMW(80);
        link.setWinterHCIndirectMW(70);
        link.setSummerHPDirectMW(60);
        link.setSummerHPIndirectMW(50);
        link.setSummerHCDirectMW(40);
        link.setSummerHCIndirectMW(30);

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(link));

        when(antaresDataManagerProperties.getFlowbasedDirectory())
                .thenReturn("/flowbased");

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, false);

        // Then
        List<Map<String, Object>> links =
                (List<Map<String, Object>>) result.get("links");

        Map<String, Object> linkMap = links.get(0);

        assertEquals("FR-BE - Interco", linkMap.get("name"));
        assertEquals(FlowbasedLinkCapacityType.ENABLED,
                linkMap.get("transmission_capacities"));

        assertEquals(100, linkMap.get("winter_HP_direct_MW"));
        assertEquals(90, linkMap.get("winter_HP_indirect_MW"));
        assertEquals(80, linkMap.get("winter_HC_direct_MW"));
        assertEquals(70, linkMap.get("winter_HC_indirect_MW"));
        assertEquals(60, linkMap.get("summer_HP_direct_MW"));
        assertEquals(50, linkMap.get("summer_HP_indirect_MW"));
        assertEquals(40, linkMap.get("summer_HC_direct_MW"));
        assertEquals(30, linkMap.get("summer_HC_indirect_MW"));
    }

    @Test
    void shouldNotAddCapacitiesForInfiniteLink() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("study###file");

        FlowbasedLinkCapacityEntity link = new FlowbasedLinkCapacityEntity();
        link.setName("FR-BE - Interco");
        link.setType(FlowbasedLinkCapacityType.INFINITE);

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(link));

        when(antaresDataManagerProperties.getFlowbasedDirectory())
                .thenReturn("/flowbased");

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, false);

        // Then
        List<Map<String, Object>> links =
                (List<Map<String, Object>>) result.get("links");

        Map<String, Object> linkMap = links.get(0);

        assertEquals("FR-BE - Interco", linkMap.get("name"));
        assertEquals(
                FlowbasedLinkCapacityType.INFINITE,
                linkMap.get("transmission_capacities")
        );

        assertFalse(linkMap.containsKey("winter_HP_direct_MW"));
        assertFalse(linkMap.containsKey("winter_HP_indirect_MW"));
        assertFalse(linkMap.containsKey("winter_HC_direct_MW"));
        assertFalse(linkMap.containsKey("winter_HC_indirect_MW"));
        assertFalse(linkMap.containsKey("summer_HP_direct_MW"));
        assertFalse(linkMap.containsKey("summer_HP_indirect_MW"));
        assertFalse(linkMap.containsKey("summer_HC_direct_MW"));
        assertFalse(linkMap.containsKey("summer_HC_indirect_MW"));

    }

    @Test
    void shouldNotAddTypeDaysWhenRecalculateIsFalse() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("study###trajectory");

        FlowbasedTypeDayEntity typeDay = new FlowbasedTypeDayEntity();

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(typeDay));

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(antaresDataManagerProperties.getFlowbasedDirectory())
                .thenReturn("/flowbased");

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, false);

        // Then
        assertFalse(result.containsKey("type_days"));
        assertEquals(false, result.get("recalculate_ts"));
    }

    @Test
    void shouldNotAddOptionalSectionsWhenRepositoriesReturnEmptyLists() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("study###trajectory.txt");

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(antaresDataManagerProperties.getFlowbasedDirectory())
                .thenReturn("/flowbased");

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, true);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.containsKey("recalculate_ts"));
        assertTrue(result.containsKey("ts_path"));

        assertFalse(result.containsKey("type_days"));
        assertFalse(result.containsKey("virtual_nodes"));
        assertFalse(result.containsKey("links"));
    }

    @Test
    void shouldBuildTsPathCorrectly() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("myStudy###input");

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(antaresDataManagerProperties.getFlowbasedDirectory())
                .thenReturn("/flowbased");

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, true);

        assertTrue(result.containsKey("ts_path"));
        // Then
        assertEquals("/flowbased/myStudy/input", result.get("ts_path"));

    }

    @Test
    void shouldNotAddNullCapacitiesForNonInfiniteLink() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("study###file");

        FlowbasedLinkCapacityEntity link = new FlowbasedLinkCapacityEntity();
        link.setName("FR-BE - Interco");
        link.setType(FlowbasedLinkCapacityType.ENABLED);
        // Only some values set, others left null
        link.setWinterHPDirectMW(100);
        link.setSummerHCIndirectMW(30);

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(link));

        when(antaresDataManagerProperties.getFlowbasedDirectory())
                .thenReturn("/flowbased");

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, false);

        // Then
        List<Map<String, Object>> links =
                (List<Map<String, Object>>) result.get("links");

        Map<String, Object> linkMap = links.get(0);

        assertEquals("FR-BE - Interco", linkMap.get("name"));
        assertEquals(FlowbasedLinkCapacityType.ENABLED, linkMap.get("transmission_capacities"));

        // Present values
        assertEquals(100, linkMap.get("winter_HP_direct_MW"));
        assertEquals(30, linkMap.get("summer_HC_indirect_MW"));

        // Null values must not be present in the map
        assertFalse(linkMap.containsKey("winter_HP_indirect_MW"));
        assertFalse(linkMap.containsKey("winter_HC_direct_MW"));
        assertFalse(linkMap.containsKey("winter_HC_indirect_MW"));
        assertFalse(linkMap.containsKey("summer_HP_direct_MW"));
        assertFalse(linkMap.containsKey("summer_HP_indirect_MW"));
        assertFalse(linkMap.containsKey("summer_HC_direct_MW"));
    }

    @Test
    void shouldNotAddNullFieldsInTypeDaysMap() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("study###file");

        FlowbasedTypeDayEntity typeDay = new FlowbasedTypeDayEntity();
        typeDay.setIdTypeDay(1);
        // clustering and classDay are left null

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(typeDay));

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(antaresDataManagerProperties.getFlowbasedDirectory())
                .thenReturn("/flowbased");

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, true);

        // Then
        List<Map<String, Object>> typeDays =
                (List<Map<String, Object>>) result.get("type_days");

        Map<String, Object> typeDayMap = typeDays.get(0);

        assertEquals(1, typeDayMap.get("id_type_day"));
        assertFalse(typeDayMap.containsKey("clustering"));
        assertFalse(typeDayMap.containsKey("class_day"));
    }
}