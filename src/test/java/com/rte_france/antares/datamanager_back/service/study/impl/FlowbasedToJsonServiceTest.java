package com.rte_france.antares.datamanager_back.service.study.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FlowbasedLinkCapacityType;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.FlowbasedConstraintsRestrictionsRepository;
import com.rte_france.antares.datamanager_back.repository.FlowbasedLinkListRepository;
import com.rte_france.antares.datamanager_back.repository.FlowbasedLinkCapacityRepository;
import com.rte_france.antares.datamanager_back.repository.FlowbasedTypeDaysRepository;
import com.rte_france.antares.datamanager_back.repository.FlowbasedVirtualNodesRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkCapacityEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedConstraintsRestrictionEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedLinkListEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedTypeDayEntity;
import com.rte_france.antares.datamanager_back.repository.model.flowbased.FlowbasedVirtualNodesEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FlowbasedToJsonServiceTest {
    @Mock
    private FlowbasedVirtualNodesRepository flowbasedVirtualNodesRepository;
    @Mock
    private FlowbasedTypeDaysRepository flowbasedTypeDaysRepository;
    @Mock
    private FlowbasedLinkCapacityRepository flowbasedLinkCapacityRepository;
    @Mock
    private FlowbasedConstraintsRestrictionsRepository flowbasedConstraintsRestrictionsRepository;
    @Mock
    private FlowbasedLinkListRepository flowbasedLinkListRepository;
    @Spy
    private AntaresDataManagerProperties antaresDataManagerProperties = new AntaresDataManagerProperties();
    @InjectMocks
    private FlowbasedToJsonService flowbasedToJsonService;

    @BeforeEach
    void setUp() {
        antaresDataManagerProperties.flowbasedDirectory = "/flowbased";
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
        link.setType(FlowbasedLinkCapacityType.ENABLED);

        when(flowbasedTypeDaysRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(typeDay));

        when(flowbasedVirtualNodesRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(virtualNode));

        when(flowbasedLinkCapacityRepository.findEntitiesByTrajectoryId(1))
                .thenReturn(List.of(link));

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, true);

        // Then
        assertEquals(true, result.get("recalculate_ts"));
        assertTrue(result.containsKey("type_days"));
        assertTrue(result.containsKey("virtual_nodes"));
        assertTrue(result.containsKey("links"));
        assertFalse(result.containsKey("areas_necessary"));

        assertEquals("/flowbased/study/trajectory.txt", result.get("ts_path"));

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

        // When
        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, true);

        // Then
        assertEquals(Map.of("recalculate_ts", true, "ts_path", "/flowbased/study/trajectory.txt"), result);

        assertFalse(result.containsKey("type_days"));
        assertFalse(result.containsKey("virtual_nodes"));
        assertFalse(result.containsKey("links"));
        assertFalse(result.containsKey("restriction_ahc_constraints"));
        verifyNoInteractions(flowbasedLinkListRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSerializeRestrictionsWithTheirOwnTrajectoryLinkLists(boolean recalculate) throws Exception {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(42).fileName("study###trajectory").horizon("2026-2027").build();
        var first = FlowbasedConstraintsRestrictionEntity.builder()
                .name("g2p_euest").enabled(true).type("hourly").sign("equal")
                .listLinks("east").constraintType("flowbased").secondMember("ts10GW").build();
        var second = first.toBuilder().name("g2p_west").enabled(false)
                .sign("less").listLinks("west").secondMember("ts20GW").build();
        var third = first.toBuilder().name("g2p_east_second").build();
        when(flowbasedConstraintsRestrictionsRepository.findEntitiesByTrajectoryId(42))
                .thenReturn(List.of(first, second, third));
        when(flowbasedLinkListRepository.findEntitiesByTrajectoryIdAndListName(42, "east"))
                .thenReturn(List.of(
                        FlowbasedLinkListEntity.builder().link("fr%de").poids(-1.0).build(),
                        FlowbasedLinkListEntity.builder().link("de%pl").poids(0.25).build(),
                        FlowbasedLinkListEntity.builder().link("fr%de").poids(0.0).build()));
        when(flowbasedLinkListRepository.findEntitiesByTrajectoryIdAndListName(42, "west"))
                .thenReturn(List.of(FlowbasedLinkListEntity.builder().link("fr%es").poids(1.0).build()));

        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, recalculate);

        ObjectMapper mapper = new ObjectMapper();
        assertEquals(mapper.readTree("""
                [
                  {"name":"g2p_euest","enabled":true,"type":"hourly","operator":"equal",
                   "links":[{"name":"fr%de","weight":-1.0},{"name":"de%pl","weight":0.25},{"name":"fr%de","weight":0.0}],
                   "series":"/study/2027/restriction_ahc/ts/ts10GW.txt"},
                  {"name":"g2p_west","enabled":false,"type":"hourly","operator":"less","links":[{"name":"fr%es","weight":1.0}],
                   "series":"/study/2027/restriction_ahc/ts/ts20GW.txt"},
                  {"name":"g2p_east_second","enabled":true,"type":"hourly","operator":"equal",
                   "links":[{"name":"fr%de","weight":-1.0},{"name":"de%pl","weight":0.25},{"name":"fr%de","weight":0.0}],
                   "series":"/study/2027/restriction_ahc/ts/ts10GW.txt"}
                ]
                """), mapper.valueToTree(result.get("restriction_ahc_constraints")));
        verify(flowbasedLinkListRepository).findEntitiesByTrajectoryIdAndListName(42, "east");
        verify(flowbasedLinkListRepository).findEntitiesByTrajectoryIdAndListName(42, "west");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027", "2026-2027", "2027-2026"})
    void shouldBuildSeriesPathUsingLargestHorizonYear(String horizon) {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(42).fileName("Porygon_CNEC_interne_2027_AFL###input").horizon(horizon).build();
        when(flowbasedConstraintsRestrictionsRepository.findEntitiesByTrajectoryId(42))
                .thenReturn(List.of(FlowbasedConstraintsRestrictionEntity.builder()
                        .name("g2p_euest").enabled(true).listLinks("east").secondMember("ts10GW").build()));
        when(flowbasedLinkListRepository.findEntitiesByTrajectoryIdAndListName(42, "east"))
                .thenReturn(List.of(FlowbasedLinkListEntity.builder().link("fr%de").poids(1.0).build()));

        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, false);

        var restrictions = new ObjectMapper().valueToTree(result.get("restriction_ahc_constraints"));
        assertEquals("/Porygon_CNEC_interne_2027_AFL/2027/restriction_ahc/ts/ts10GW.txt",
                restrictions.get(0).get("series").asText());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "../ts10GW", "/ts10GW", "nested/ts10GW", "nested\\ts10GW", ".", ".."})
    void shouldRejectMissingOrUnsafeSecondMember(String secondMember) {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(42).fileName("study###input").horizon("2026-2027").build();
        when(flowbasedConstraintsRestrictionsRepository.findEntitiesByTrajectoryId(42))
                .thenReturn(List.of(FlowbasedConstraintsRestrictionEntity.builder()
                        .name("g2p_euest").enabled(false).listLinks("east").secondMember(secondMember).build()));
        when(flowbasedLinkListRepository.findEntitiesByTrajectoryIdAndListName(42, "east"))
                .thenReturn(List.of(FlowbasedLinkListEntity.builder().link("fr%de").poids(1.0).build()));

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> flowbasedToJsonService.buildFlowbasedMap(trajectory, false));

        assertTrue(exception.getMessage().contains("second_member"));
        assertTrue(exception.getMessage().contains("g2p_euest"));
        assertTrue(exception.getMessage().contains("42"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2026/2027", "2027-extra", "27", "2026-2027-2028"})
    void shouldRejectInvalidSeriesHorizon(String horizon) {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(42).fileName("study###input").horizon(horizon).build();
        when(flowbasedConstraintsRestrictionsRepository.findEntitiesByTrajectoryId(42))
                .thenReturn(List.of(FlowbasedConstraintsRestrictionEntity.builder()
                        .name("g2p_euest").listLinks("east").secondMember("ts10GW").build()));
        when(flowbasedLinkListRepository.findEntitiesByTrajectoryIdAndListName(42, "east"))
                .thenReturn(List.of(FlowbasedLinkListEntity.builder().link("fr%de").poids(1.0).build()));

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> flowbasedToJsonService.buildFlowbasedMap(trajectory, true));

        assertTrue(exception.getMessage().contains("horizon"));
    }

    @Test
    void shouldRejectNullRestrictionLinkWeight() {
        assertInvalidRestrictionLinkWeight(null);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void shouldRejectNonFiniteRestrictionLinkWeight(double weight) {
        assertInvalidRestrictionLinkWeight(weight);
    }

    private void assertInvalidRestrictionLinkWeight(Double weight) {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(42).fileName("study###input").horizon("2027").build();
        when(flowbasedConstraintsRestrictionsRepository.findEntitiesByTrajectoryId(42))
                .thenReturn(List.of(FlowbasedConstraintsRestrictionEntity.builder()
                        .name("restriction").listLinks("east").secondMember("series").build()));
        when(flowbasedLinkListRepository.findEntitiesByTrajectoryIdAndListName(42, "east"))
                .thenReturn(List.of(FlowbasedLinkListEntity.builder()
                        .listName("east").link("ch-fr").poids(weight).build()));

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> flowbasedToJsonService.buildFlowbasedMap(trajectory, false));

        assertTrue(exception.getMessage().contains("Invalid weight"));
        assertTrue(exception.getMessage().contains("ch-fr"));
        assertTrue(exception.getMessage().contains("east"));
    }

    @Test
    void shouldRejectRestrictionWithMissingLinkList() {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(42).fileName("study###trajectory").build();
        when(flowbasedConstraintsRestrictionsRepository.findEntitiesByTrajectoryId(42))
                .thenReturn(List.of(FlowbasedConstraintsRestrictionEntity.builder()
                        .name("g2p_euest").enabled(true).listLinks("missing").build()));

        TechnicalException exception = assertThrows(TechnicalException.class,
                () -> flowbasedToJsonService.buildFlowbasedMap(trajectory, true));

        assertTrue(exception.getMessage().contains("missing"));
        assertTrue(exception.getMessage().contains("42"));
        verify(flowbasedLinkListRepository).findEntitiesByTrajectoryIdAndListName(42, "missing");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldEmitTsPath(boolean recalculate) {
        TrajectoryEntity trajectory = new TrajectoryEntity();
        trajectory.setId(1);
        trajectory.setFileName("myStudy###input");

        Map<String, Object> result = flowbasedToJsonService.buildFlowbasedMap(trajectory, recalculate);

        assertEquals(Map.of("recalculate_ts", recalculate, "ts_path", "/flowbased/myStudy/input"), result);
        assertEquals("/flowbased/myStudy/input", new ObjectMapper().valueToTree(result).get("ts_path").asText());
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