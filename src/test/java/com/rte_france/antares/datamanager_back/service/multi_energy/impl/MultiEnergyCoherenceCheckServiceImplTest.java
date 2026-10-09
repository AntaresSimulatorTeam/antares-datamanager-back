package com.rte_france.antares.datamanager_back.service.multi_energy.impl;

import com.rte_france.antares.datamanager_back.dto.ConstraintRowData;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaConfigEntity;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.GroupAreaDescEntity;
import com.rte_france.antares.datamanager_back.repository.model.HydroCapacityMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.ListAreaDescEntity;
import com.rte_france.antares.datamanager_back.repository.model.LoadEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiEnergyCoherenceCheckServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private AreaRepository areaRepository;

    @InjectMocks
    private MultiEnergyCoherenceCheckServiceImpl coherenceCheckService;

    private Integer studyId;

    @BeforeEach
    void setUp() {
        studyId = 1;
    }

    private TrajectoryEntity createAreaMeTrajectory(String... areaNames) {
        List<AreaConfigEntity> areaConfigs = List.of();
        if (areaNames != null) {
            areaConfigs = java.util.Arrays.stream(areaNames)
                    .map(name -> AreaConfigEntity.builder()
                            .area(AreaEntity.builder().name(name).build())
                            .build())
                    .toList();
        }
        return TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(areaConfigs)
                .build();
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenLoadMeContainsAllAreaMeAreas_shouldPass() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR", "BE");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        LoadEntity load1 = LoadEntity.builder().area("FR").build();
        LoadEntity load2 = LoadEntity.builder().area("be").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(load1, load2))
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenLoadMeIsMissingArea_shouldThrowBusinessException() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR", "BE", "DE");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        LoadEntity load1 = LoadEntity.builder().area("FR").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(load1))
                .build();

        assertThatThrownBy(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        ).isInstanceOf(BusinessException.class)
                .hasMessage("Areas {0} from AREA_ME trajectory is (are) not present in {1} trajectory")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorMessageArguments()).hasSize(2);
                    assertThat(be.getErrorMessageArguments().get(1)).isEqualTo(TrajectoryType.LOAD_ME.name());
                    String missingAreas = be.getErrorMessageArguments().get(0);
                    assertThat(missingAreas).contains("BE");
                    assertThat(missingAreas).contains("DE");
                });
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenStsMeContainsAllAreaMeAreas_shouldPass() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR", "BE");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        StStorageEntity sts1 = StStorageEntity.builder().area("FR").build();
        StStorageEntity sts2 = StStorageEntity.builder().area(" BE ").build();
        TrajectoryEntity stsMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS_ME.name())
                .stStorageEntities(List.of(sts1, sts2))
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.STS_ME.name(), stsMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenStsMeIsMissingArea_shouldThrowBusinessException() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR", "BE");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        StStorageEntity sts1 = StStorageEntity.builder().area("FR").build();
        TrajectoryEntity stsMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS_ME.name())
                .stStorageEntities(List.of(sts1))
                .build();

        assertThatThrownBy(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.STS_ME.name(), stsMeTrajectory)
        ).isInstanceOf(BusinessException.class)
                .hasMessage("Areas {0} from AREA_ME trajectory is (are) not present in {1} trajectory")
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getErrorMessageArguments()).containsExactly("BE", TrajectoryType.STS_ME.name());
                });
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenNoAreaMeTrajectories_shouldPass() {
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(Collections.emptyList());

        TrajectoryEntity stsMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS_ME.name())
                .stStorageEntities(Collections.emptyList())
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.STS_ME.name(), stsMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenAreaMeHasNullOrEmptyAreaConfigs_shouldPass() {
        TrajectoryEntity areaMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA_ME.name())
                .areaConfigEntities(null)
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(null)
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenEntitiesHaveNullOrBlankAreas_shouldFilterThemOut() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        LoadEntity loadWithNullArea = LoadEntity.builder().area(null).build();
        LoadEntity loadWithBlankArea = LoadEntity.builder().area("   ").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(loadWithNullArea, loadWithBlankArea))
                .build();

        assertThatThrownBy(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        ).isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorMessageArguments()).containsExactly("FR", TrajectoryType.LOAD_ME.name());
                });
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenTrajectoryIsNull_shouldNotThrowException() {
        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.LOAD_ME.name(), null)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenTrajectoryTypeIsNull_shouldNotThrowException() {
        LoadEntity load = LoadEntity.builder().area("FR").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(load))
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, null, loadMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenStudyIdIsNull_shouldNotThrowException() {
        LoadEntity load = LoadEntity.builder().area("FR").build();
        TrajectoryEntity loadMeTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.LOAD_ME.name())
                .loadEntities(Set.of(load))
                .build();

        assertDoesNotThrow(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(null, TrajectoryType.LOAD_ME.name(), loadMeTrajectory)
        );
    }

    @Test
    void checkAreaMETrajectoryConsistency_whenTrajectoryTypeIsOther_andAreaMeNotEmpty_shouldThrowException() {
        TrajectoryEntity areaMeTrajectory = createAreaMeTrajectory("FR");
        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.AREA_ME.name(), studyId))
                .thenReturn(List.of(areaMeTrajectory));

        TrajectoryEntity thermalTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.THERMAL_CAPACITY_ME.name())
                .build();

        assertThatThrownBy(() ->
                coherenceCheckService.checkAreaMETrajectoryConsistency(studyId, TrajectoryType.THERMAL_CAPACITY_ME.name(), thermalTrajectory)
        ).isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorMessageArguments()).containsExactly("FR", TrajectoryType.THERMAL_CAPACITY_ME.name());
                });
    }

    // ==================== validateHydroCapacityMeCoherence ====================

    private TrajectoryEntity createHydroCapacityMeTrajectory(String... nodes) {
        List<HydroCapacityMeEntity> entities = Arrays.stream(nodes)
                .map(node -> HydroCapacityMeEntity.builder().node(node).build())
                .toList();
        return TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_CAPACITY_ME.name())
                .hydroCapacityMeEntities(entities)
                .build();
    }

    private void stubAreaMeAreas(String... names) {
        List<AreaEntity> areas = Arrays.stream(names)
                .map(name -> AreaEntity.builder().name(name).build())
                .toList();
        when(areaRepository.findAllByStudyId(studyId, TrajectoryType.AREA_ME.name())).thenReturn(areas);
    }

    @Test
    void validateHydroCapacityMeCoherence_whenAllNodesArePresentInAreaMe_shouldPass() {
        stubAreaMeAreas("FR", "CH");
        TrajectoryEntity trajectory = createHydroCapacityMeTrajectory("fr", "CH");

        assertDoesNotThrow(() -> coherenceCheckService.validateHydroCapacityMeCoherence(studyId, trajectory));
    }

    @Test
    void validateHydroCapacityMeCoherence_whenNoHydroCapacityMeEntities_shouldPass() {
        stubAreaMeAreas("FR");
        TrajectoryEntity trajectory = createHydroCapacityMeTrajectory();

        assertDoesNotThrow(() -> coherenceCheckService.validateHydroCapacityMeCoherence(studyId, trajectory));
    }

    @Test
    void validateHydroCapacityMeCoherence_whenNodesMissingFromAreaMe_shouldThrowWithOnlyMissingNodes() {
        stubAreaMeAreas("FR");
        TrajectoryEntity trajectory = createHydroCapacityMeTrajectory("FR", "DE", "ES");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> coherenceCheckService.validateHydroCapacityMeCoherence(studyId, trajectory));

        assertEquals("Areas {0} from HYDRO_ME Capacity trajectory is (are) not present in AREAS_ME trajectory", exception.getMessage());
        assertEquals(1, exception.getErrorMessageArguments().size());
        String missing = exception.getErrorMessageArguments().get(0);
        assertTrue(missing.contains("de"), "Missing nodes should contain de: " + missing);
        assertTrue(missing.contains("es"), "Missing nodes should contain es: " + missing);
        assertFalse(missing.contains("fr"), "Present node must not be reported: " + missing);
    }

    @Test
    void validateHydroCapacityMeCoherence_whenAreaMeIsEmpty_shouldThrowWithAllNodes() {
        stubAreaMeAreas();
        TrajectoryEntity trajectory = createHydroCapacityMeTrajectory("FR");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> coherenceCheckService.validateHydroCapacityMeCoherence(studyId, trajectory));

        assertEquals(List.of("fr"), exception.getErrorMessageArguments());
    }

    // ==================== validateConstraintMeCoherence ====================

    private TrajectoryEntity createConstraintMeTrajectory(String... groupAreas) {
        List<ListAreaDescEntity> areas = Arrays.stream(groupAreas)
                .map(area -> ListAreaDescEntity.builder().area(area).build())
                .toList();
        GroupAreaDescEntity group = GroupAreaDescEntity.builder().groupName("GROUP").areas(areas).build();
        return TrajectoryEntity.builder()
                .type(TrajectoryType.CONSTRAINT_ME.name())
                .groupAreaDescEntities(List.of(group))
                .build();
    }

    private void stubAreaTrajectoryAreas(String... names) {
        List<AreaEntity> areas = Arrays.stream(names)
                .map(name -> AreaEntity.builder().name(name).build())
                .toList();
        when(areaRepository.findAllByStudyId(studyId, TrajectoryType.AREA.name())).thenReturn(areas);
    }

    @Test
    void validateConstraintMeCoherence_whenAllGroupAreasArePresentInAreaTrajectory_shouldPass() {
        stubAreaTrajectoryAreas("FR", "CH", "IT");
        TrajectoryEntity trajectory = createConstraintMeTrajectory("FR", "ch");

        assertDoesNotThrow(() -> coherenceCheckService.validateConstraintMeCoherence(studyId, trajectory));
    }

    @Test
    void validateConstraintMeCoherence_whenGroupAreasMissingFromAreaTrajectory_shouldThrowWithOnlyMissingAreas() {
        stubAreaTrajectoryAreas("FR");
        TrajectoryEntity trajectory = createConstraintMeTrajectory("FR", "DE", "ES");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> coherenceCheckService.validateConstraintMeCoherence(studyId, trajectory));

        assertEquals("Areas {0} from CONSTRAINT_ME trajectory is (are) not present in AREA trajectory", exception.getMessage());
        assertEquals(1, exception.getErrorMessageArguments().size());
        String missing = exception.getErrorMessageArguments().get(0);
        assertTrue(missing.contains("de"), "Missing areas should contain de: " + missing);
        assertTrue(missing.contains("es"), "Missing areas should contain es: " + missing);
        assertFalse(missing.contains("fr"), "Present area must not be reported: " + missing);
    }

    @Test
    void validateConstraintMeCoherence_whenNoGroupAreaDescEntities_shouldPass() {
        stubAreaTrajectoryAreas("FR");
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.CONSTRAINT_ME.name())
                .groupAreaDescEntities(List.of())
                .build();

        assertDoesNotThrow(() -> coherenceCheckService.validateConstraintMeCoherence(studyId, trajectory));
    }

    // ==================== extractNodesFromAreaMeTrajectory ====================

    @Test
    void extractNodesFromAreaMeTrajectory_shouldReturnLowerCaseAreaNames() {
        stubAreaMeAreas("FR", "Ch", "it");

        Set<String> nodes = coherenceCheckService.extractNodesFromAreaMeTrajectory(studyId);

        assertThat(nodes).containsExactlyInAnyOrder("fr", "ch", "it");
    }

    @Test
    void extractNodesFromAreaMeTrajectory_whenNoArea_shouldReturnEmptySet() {
        stubAreaMeAreas();

        assertThat(coherenceCheckService.extractNodesFromAreaMeTrajectory(studyId)).isEmpty();
    }

    // ==================== validateConstraintData ====================

    private ConstraintRowData constraintRow(String left1, String left2, String clusterLeft,
                                            String right1, String right2, String clusterRight) {
        return ConstraintRowData.builder()
                .noeud1Gauche(left1)
                .noeud2Gauche(left2)
                .clusterGauche(clusterLeft)
                .noeud1Droite(right1)
                .noeud2Droite(right2)
                .clusterDroite(clusterRight)
                .build();
    }

    @Test
    void validateConstraintData_whenAllNodesAndClustersAreValid_shouldPass() {
        Set<String> areaMeNodes = Set.of("fr", "ch");
        Set<String> groupAreas = Set.of("grp_a");
        Set<String> groupClusters = Set.of("cl_1");

        ConstraintRowData row = constraintRow("FR", "ch", "CL_1", "grp_a", "grp_a", "cl_1");

        assertDoesNotThrow(() ->
                coherenceCheckService.validateConstraintData(row, areaMeNodes, groupAreas, groupClusters, "2021"));
    }

    @Test
    void validateConstraintData_whenLeftNodeNotInAreaMe_shouldThrowNodeMustBelongAreaMe() {
        ConstraintRowData row = constraintRow("DE", null, null, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                coherenceCheckService.validateConstraintData(row, Set.of("fr"), Set.of(), Set.of(), "2021"));

        assertEquals("Node {0} in {1} tab must belong to AREA_ME trajectory", exception.getMessage());
        assertEquals(List.of("DE", "2021"), exception.getErrorMessageArguments());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void validateConstraintData_whenRightNodeNotInGroupArea_shouldThrowNodeMustBelongAreaDesc() {
        ConstraintRowData row = constraintRow(null, null, null, "XX", null, null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                coherenceCheckService.validateConstraintData(row, Set.of(), Set.of("grp_a"), Set.of(), "2022"));

        assertEquals("Node {0} in {1} tab must belong to listArea_desc tab", exception.getMessage());
        assertEquals(List.of("XX", "2022"), exception.getErrorMessageArguments());
    }

    @Test
    void validateConstraintData_whenLeftClusterNotInGroupClusters_shouldThrowClusterMustBelongDesc() {
        ConstraintRowData row = constraintRow(null, null, "unknown_cluster", null, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                coherenceCheckService.validateConstraintData(row, Set.of(), Set.of(), Set.of("cl_1"), "2023"));

        assertEquals("Cluster {0} in {1} tab must belong to listCluster_desc tab", exception.getMessage());
        assertEquals(List.of("unknown_cluster", "2023"), exception.getErrorMessageArguments());
    }

    @Test
    void validateConstraintData_whenRightClusterNotInGroupClusters_shouldThrowClusterMustBelongDesc() {
        ConstraintRowData row = constraintRow(null, null, null, null, null, "unknown_right");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                coherenceCheckService.validateConstraintData(row, Set.of(), Set.of(), Set.of(), "2024"));

        assertEquals("Cluster {0} in {1} tab must belong to listCluster_desc tab", exception.getMessage());
        assertEquals(List.of("unknown_right", "2024"), exception.getErrorMessageArguments());
    }

    @Test
    void validateConstraintData_whenNodesAndClustersAreEmpty_shouldSkipValidation() {
        ConstraintRowData row = constraintRow(null, "  ", null, null, null, "   ");

        assertDoesNotThrow(() ->
                coherenceCheckService.validateConstraintData(row, Set.of(), Set.of(), Set.of(), "2025"));
    }
}
