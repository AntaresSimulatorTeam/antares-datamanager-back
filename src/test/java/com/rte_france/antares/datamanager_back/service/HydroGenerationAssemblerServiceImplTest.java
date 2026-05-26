package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.hydro.impl.HydroGenerationAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HydroGenerationAssemblerServiceImplTest {

    @Mock
    private NasFileService nasFileService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private TimeSeriesReader timeSeriesReader;

    @InjectMocks
    private HydroGenerationAssemblerServiceImpl service;

    @BeforeEach
    void setUp() {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/nas");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getHydroSeriesDirectory()).thenReturn("hydro_series");
    }

    // --- Tests existants corrigés ---

    @Test
    void assembleHydroProperties_returnsGroupedProperties() {
        // HYDRO_PARAMETERS était incorrect : le service filtre uniquement HYDRO_TECHNICAL_PARAMETERS
        HydroParametersEntity hp1 = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .interDailyBreakdown(1)
                .reservoirCapacity(new BigDecimal(1000))
                .build();
        HydroParametersEntity hp2 = HydroParametersEntity.builder()
                .node("BE")
                .followLoad(false)
                .interDailyBreakdown(2)
                .reservoirCapacity(new BigDecimal(2000))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp1, hp2))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("FR"));
        assertTrue(result.containsKey("BE"));

        List<HydroGenerationDTO> frProps = result.get("FR");
        assertEquals(1, frProps.size());
        assertEquals(Boolean.TRUE, frProps.get(0).getFollowLoadModulation());
        assertEquals(1000, frProps.get(0).getReservoirCapacity());

        List<HydroGenerationDTO> beProps = result.get("BE");
        assertEquals(1, beProps.size());
        assertEquals(Boolean.FALSE, beProps.get(0).getFollowLoadModulation());
        assertEquals(2000, beProps.get(0).getReservoirCapacity());
    }

    @Test
    void assembleHydroProperties_filtersOutOtherTrajectoryTypes() {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA.name())
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void assembleHydroProperties_includesAllocations() {
        // Paramètres ET allocations proviennent du même type HYDRO_TECHNICAL_PARAMETERS
        HydroParametersEntity hp1 = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .build();

        HydroAllocationEntity ha1 = HydroAllocationEntity.builder()
                .hydro("FR")
                .load("AT")
                .allocation(1)
                .build();
        HydroAllocationEntity ha2 = HydroAllocationEntity.builder()
                .hydro("FR")
                .load("DE")
                .allocation(2)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp1))
                .hydroAllocationEntities(List.of(ha1, ha2))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        assertNotNull(result);
        assertTrue(result.containsKey("FR"));
        List<HydroGenerationDTO> frDto = result.get("FR");
        assertEquals(1, frDto.size());
        Map<String, Double> allocation = frDto.get(0).getAllocation();
        assertNotNull(allocation);
        assertEquals(2, allocation.size());
        assertEquals(1.0, allocation.get("AT"));
        assertEquals(2.0, allocation.get("DE"));
    }

    // --- Nouveaux tests ---

    @Test
    void assembleHydroProperties_emptyTrajectories() {
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of())
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void assembleHydroProperties_filtersParameterWithNullNode() {
        HydroParametersEntity hpWithNode = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .build();
        HydroParametersEntity hpNullNode = HydroParametersEntity.builder()
                .node(null)
                .followLoad(false)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hpWithNode, hpNullNode))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR"));
        assertFalse(result.containsKey(null));
    }

    @Test
    void assembleHydroProperties_nodeKeyIsUppercased() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("fr")
                .followLoad(true)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        assertTrue(result.containsKey("FR"));
        assertFalse(result.containsKey("fr"));
    }

    @Test
    void assembleHydroProperties_allocationWithNullAmountDefaultsToZero() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("FR")
                .build();

        HydroAllocationEntity ha = HydroAllocationEntity.builder()
                .hydro("FR")
                .load("DE")
                .allocation(null)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(ha))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        Map<String, Double> allocation = result.get("FR").get(0).getAllocation();
        assertNotNull(allocation);
        assertEquals(0.0, allocation.get("DE"));
    }

    @Test
    void assembleHydroProperties_allocationSkippedWhenHydroNodeNotInParameters() {
        // Allocation référence "ES" qui n'a pas de paramètres → pas de DTO → allocation ignorée
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("FR")
                .build();

        HydroAllocationEntity ha = HydroAllocationEntity.builder()
                .hydro("ES")
                .load("FR")
                .allocation(5)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(ha))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        // FR existe mais son DTO n'a pas d'allocation car l'allocation cible ES (absent)
        List<HydroGenerationDTO> frDtos = result.get("FR");
        assertEquals(1, frDtos.size());
        assertNull(frDtos.get(0).getAllocation());
        // ES n'est pas dans le résultat
        assertFalse(result.containsKey("ES"));
    }

    @Test
    void assembleHydroProperties_filtersAllocationWithNullLoad() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("FR")
                .build();

        HydroAllocationEntity haWithLoad = HydroAllocationEntity.builder()
                .hydro("FR")
                .load("DE")
                .allocation(3)
                .build();
        HydroAllocationEntity haNullLoad = HydroAllocationEntity.builder()
                .hydro("FR")
                .load(null)
                .allocation(5)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(haWithLoad, haNullLoad))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        Map<String, Double> allocation = result.get("FR").get(0).getAllocation();
        assertNotNull(allocation);
        assertEquals(1, allocation.size());
        assertEquals(3.0, allocation.get("DE"));
        assertFalse(allocation.containsKey(null));
    }

    @Test
    void assembleHydroProperties_filtersAllocationWithNullHydro() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("FR")
                .build();

        HydroAllocationEntity haNullHydro = HydroAllocationEntity.builder()
                .hydro(null)
                .load("DE")
                .allocation(5)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(haNullHydro))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        // FR existe mais sans allocation car l'allocation a hydro null (filtrée)
        List<HydroGenerationDTO> frDtos = result.get("FR");
        assertEquals(1, frDtos.size());
        assertNull(frDtos.get(0).getAllocation());
    }

    @Test
    void assembleHydroProperties_multipleParametersForSameNode() {
        HydroParametersEntity hp1 = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .interDailyBreakdown(1)
                .build();
        HydroParametersEntity hp2 = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(false)
                .interDailyBreakdown(2)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp1, hp2))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        assertEquals(1, result.size());
        List<HydroGenerationDTO> frDtos = result.get("FR");
        assertEquals(2, frDtos.size());
    }

    @Test
    void assembleHydroProperties_allMappedFieldsFromEntity() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .interDailyBreakdown(1)
                .interDailyModulation(2)
                .interMonthlyBreakdown(3)
                .reservoir(true)
                .reservoirCapacity(new BigDecimal(5000))
                .pumpingEfficiency(90)
                .initializeReservoirDate(7)
                .useWater(false)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        HydroGenerationDTO dto = result.get("FR").get(0);
        assertEquals(Boolean.TRUE, dto.getFollowLoadModulation());
        assertEquals(1, dto.getInterDailyBreakdown());
        assertEquals(2, dto.getInterDailyModulation());
        assertEquals(3, dto.getInterMonthlyBreakdown());
        assertEquals(Boolean.TRUE, dto.getReservoirManagement());
        assertEquals(5000, dto.getReservoirCapacity());
        assertEquals(90, dto.getPumpingEfficiency());
        assertEquals(7, dto.getInitializeReservoirDate());
        assertEquals(Boolean.FALSE, dto.getUseWater());
    }

    @Test
    void assembleHydroProperties_allocationKeyIsUppercased() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("fr")
                .build();

        HydroAllocationEntity ha = HydroAllocationEntity.builder()
                .hydro("fr")
                .load("de")
                .allocation(10)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(ha))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, List<HydroGenerationDTO>> result = service.assembleHydroProperties(studyEntity);

        assertTrue(result.containsKey("FR"));
        Map<String, Double> allocation = result.get("FR").get(0).getAllocation();
        assertNotNull(allocation);
        assertTrue(allocation.containsKey("DE"));
        assertEquals(10.0, allocation.get("DE"));
    }
}
