package com.rte_france.antares.datamanager_back.service.study.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.repository.FlowbasedLinkCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.FlowbasedTypeDaysRepository;
import com.rte_france.antares.datamanager_back.repository.FlowbasedVirtualNodesRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;


class FlowbasedToJsonServiceTest {
    @Mock
    private FlowbasedVirtualNodesRepository flowbasedVirtualNodesRepository;
    @Mock
    private FlowbasedTypeDaysRepository flowbasedTypeDaysRepository;
    @Mock
    private FlowbasedLinkCapacityRepository flowbasedLinkCapacityRepository;
    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    private FlowbasedToJsonService flowbasedToJsonService;

    @BeforeEach
    void setUp() {
        flowbasedToJsonService = new FlowbasedToJsonService(flowbasedVirtualNodesRepository, flowbasedTypeDaysRepository, flowbasedLinkCapacityRepository, antaresDataManagerProperties);
    }

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

        assertEquals(List.of("FR"), virtualNodes);

        List<Map<String, Object>> links =
                (List<Map<String, Object>>) result.get("links");

        assertEquals("FR", links.get(0).get("name"));
    }

    @Test
    void shouldNotAddTypeDaysWhenRecalculateIsFalse() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("study###trajectory.txt");

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
        trajectory.setFileName("myStudy###input.txt");

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(Collections.emptyList());

        when(antaresDataManagerProperties.getFlowbasedDirectory());

    }
}