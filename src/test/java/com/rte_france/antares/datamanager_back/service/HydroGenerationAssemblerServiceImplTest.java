package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.HydroAreaGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.HydroGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.hydro.impl.HydroGenerationAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
        lenient().when(antaresDataManagerProperties.getPspSeriesDirectory()).thenReturn("psp_series");
    }

    // --- Tests existants corrigés ---

    @Test
    void assembleHydroProperties_returnsGroupedProperties() {
        HydroParametersEntity hp1 = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .interDailyBreakdown(new BigDecimal("1"))
                .reservoirCapacity(new BigDecimal(1000))
                .build();
        HydroParametersEntity hp2 = HydroParametersEntity.builder()
                .node("BE")
                .followLoad(false)
                .interDailyBreakdown(new BigDecimal("2"))
                .reservoirCapacity(new BigDecimal(2000))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp1, hp2))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("FR"));
        assertTrue(result.containsKey("BE"));

        assertEquals(Boolean.TRUE, result.get("FR").hydro().getProperties().getFollowLoadModulation());
        assertEquals(new BigDecimal("1000"), result.get("FR").hydro().getProperties().getReservoirCapacity());

        assertEquals(Boolean.FALSE, result.get("BE").hydro().getProperties().getFollowLoadModulation());
        assertEquals(new BigDecimal("2000"), result.get("BE").hydro().getProperties().getReservoirCapacity());
    }

    @Test
    void assembleHydroProperties_filtersOutOtherTrajectoryTypes() {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA.name())
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

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
                .allocation(BigDecimal.valueOf(1))
                .build();
        HydroAllocationEntity ha2 = HydroAllocationEntity.builder()
                .hydro("FR")
                .load("DE")
                .allocation(BigDecimal.valueOf(2))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp1))
                .hydroAllocationEntities(List.of(ha1, ha2))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertNotNull(result);
        assertTrue(result.containsKey("FR"));
        Map<String, Double> allocation = result.get("FR").hydro().getAllocation();
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

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

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

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR"));
        assertFalse(result.containsKey(null));
        assertNotNull(result.get("FR").hydro());
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

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

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

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        Map<String, Double> allocation = result.get("FR").hydro().getAllocation();
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
                .allocation(BigDecimal.valueOf(5))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(ha))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        // FR existe mais son DTO n'a pas d'allocation car l'allocation cible ES (absent)
        assertNotNull(result.get("FR").hydro());
        assertNull(result.get("FR").hydro().getAllocation());
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
                .allocation(BigDecimal.valueOf(3))
                .build();
        HydroAllocationEntity haNullLoad = HydroAllocationEntity.builder()
                .hydro("FR")
                .load(null)
                .allocation(BigDecimal.valueOf(5))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(haWithLoad, haNullLoad))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        Map<String, Double> allocation = result.get("FR").hydro().getAllocation();
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
                .allocation(BigDecimal.valueOf(5))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(haNullHydro))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        // FR existe mais sans allocation car l'allocation a hydro null (filtrée)
        assertNotNull(result.get("FR").hydro());
        assertNull(result.get("FR").hydro().getAllocation());
    }

    @Test
    void assembleHydroProperties_multipleParametersForSameNode() {
        HydroParametersEntity hp1 = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .interDailyBreakdown(new BigDecimal("1"))
                .build();
        HydroParametersEntity hp2 = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(false)
                .interDailyBreakdown(new BigDecimal("2"))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp1, hp2))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertEquals(1, result.size());
        assertNotNull(result.get("FR").hydro());
        // first parameter entity wins for properties
        assertEquals(Boolean.TRUE, result.get("FR").hydro().getProperties().getFollowLoadModulation());
    }

    @Test
    void assembleHydroProperties_allMappedFieldsFromEntity() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("FR")
                .followLoad(true)
                .interDailyBreakdown(new BigDecimal("1"))
                .interDailyModulation(new BigDecimal("2"))
                .interMonthlyBreakdown(new BigDecimal("3"))
                .reservoir(true)
                .reservoirCapacity(new BigDecimal(5000))
                .pumpingEfficiency(new BigDecimal("90"))
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

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        HydroGenerationDTO dto = result.get("FR").hydro();
        assertEquals(Boolean.TRUE, dto.getProperties().getFollowLoadModulation());
        assertEquals(new BigDecimal("1"), dto.getProperties().getInterDailyBreakdown());
        assertEquals(new BigDecimal("2"), dto.getProperties().getInterDailyModulation());
        assertEquals(new BigDecimal("3"), dto.getProperties().getInterMonthlyBreakdown());
        assertEquals(Boolean.TRUE, dto.getProperties().getReservoirManagement());
        assertEquals(new BigDecimal("5000"), dto.getProperties().getReservoirCapacity());
        assertEquals(new BigDecimal("90"), dto.getProperties().getPumpingEfficiency());
        assertEquals(7, dto.getProperties().getInitializeReservoirDate());
        assertEquals(Boolean.FALSE, dto.getProperties().getUseWater());
    }

    @Test
    void assembleHydroProperties_allocationKeyIsUppercased() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("fr")
                .build();

        HydroAllocationEntity ha = HydroAllocationEntity.builder()
                .hydro("fr")
                .load("de")
                .allocation(BigDecimal.valueOf(10))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(ha))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertTrue(result.containsKey("FR"));
        Map<String, Double> allocation = result.get("FR").hydro().getAllocation();
        assertNotNull(allocation);
        assertTrue(allocation.containsKey("DE"));
        assertEquals(10.0, allocation.get("DE"));
    }

    // --- Tests sur la gestion des séries HYDRO_SERIES ---

    @Test
    void assembleHydroProperties_setsSeriesOnDtoWhenMingenFileExists(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj1").resolve("mingen");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("mingen_FR_2030.xlsx"));

        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of());
        when(nasFileService.readMatrix(any(Path.class), any(), anyBoolean(), any(), any())).thenReturn(matrix);
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("FR_mingen"), eq("hydro_output")))
                .thenReturn("FR_mingen.arrow");

        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("mingen_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        HydroGenerationDTO dto = result.get("FR").hydro();
        assertNotNull(dto.getSeries());
        assertArrayEquals(new String[]{"FR_mingen.arrow"}, dto.getSeries());
    }

    @Test
    void assembleHydroProperties_setsSeriesOnDtoWhenNoTechnicalParametersTrajectoryExists(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj1").resolve("mingen");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("mingen_FR_2030.xlsx"));

        Path fileReservoirDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj2").resolve("reservoir_levels");
        Files.createDirectories(fileReservoirDir);
        Files.createFile(fileReservoirDir.resolve("reservoir_levels_FR_2030.xlsx"));

        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of());
        when(nasFileService.readMatrix(any(Path.class), any(), anyBoolean(), any(), any())).thenReturn(matrix);
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("FR_mingen"), eq("hydro_output")))
                .thenReturn("FR_mingen.arrow");

        HydroSeriesEntity hydroSeries1 = HydroSeriesEntity.builder().tsName("mingen_FR_2030.xlsx").build();
        HydroSeriesEntity hydroSeries2 = HydroSeriesEntity.builder().tsName("reservoir_levels_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries1, hydroSeries2))
                .build();
                
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        HydroGenerationDTO dto = result.get("FR").hydro();
        assertNotNull(dto.getSeries());
        assertArrayEquals(new String[]{"FR_mingen.arrow"}, dto.getSeries());
    }

    @Test
    void assembleHydroProperties_seriesNullWhenFileNotOnDisk() {
        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("mingen_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertNull(result.get("FR").hydro().getSeries());
    }

    @Test
    void assembleHydroProperties_throwsBusinessExceptionOnIOErrorSavingHydroSeries(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj1").resolve("mingen");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("mingen_FR_2030.xlsx"));

        when(nasFileService.readMatrix(any(Path.class), any(), anyBoolean(), any(), any())).thenReturn(new TimeSeriesMatrix(List.of()));
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), anyString(), anyString()))
                .thenThrow(new IOException("write error"));

        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("mingen_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assembleHydroProperties(studyEntity));
        assertEquals("Could not generate matrix for Hydro Series", ex.getMessage());
    }

    @Test
    void assembleHydroProperties_setsSeriesForMaxpowerFile(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj1");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("maxpower_2030.xlsx"));

        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of());
        when(timeSeriesReader.readSelectedColumnsFromXlsx(any(Path.class), any(), any())).thenReturn(matrix);
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("FR_maxpower"), eq("hydro_output")))
                .thenReturn("FR_maxpower.arrow");

        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("maxpower_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        HydroGenerationDTO dto = result.get("FR").hydro();
        assertNotNull(dto.getSeries());
        assertArrayEquals(new String[]{"FR_maxpower.arrow"}, dto.getSeries());
    }

    @Test
    void assembleHydroProperties_throwsBusinessExceptionOnIOErrorReadingMaxpower(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj1");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("maxpower_2030.xlsx"));

        when(timeSeriesReader.readSelectedColumnsFromXlsx(any(Path.class), any(), any()))
                .thenThrow(new IOException("read error"));

        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("maxpower_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assembleHydroProperties(studyEntity));
        assertEquals("Could not generate matrix for maxpower", ex.getMessage());
    }

    @Test
    void assembleHydroProperties_setsSeriesForRorFileUnderInflowsSubdir(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj1").resolve("inflows");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("ror_FR_2030.xlsx"));

        when(nasFileService.readMatrix(any(Path.class), any(), anyBoolean(), any(), any())).thenReturn(new TimeSeriesMatrix(List.of()));
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("FR_ror"), eq("hydro_output")))
                .thenReturn("FR_ror.arrow");

        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("ror_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        HydroGenerationDTO dto = result.get("FR").hydro();
        assertNotNull(dto.getSeries());
        assertArrayEquals(new String[]{"FR_ror.arrow"}, dto.getSeries());
    }

    @Test
    void assembleHydroProperties_reservoirLevelsKeptWhenReservoirTrue(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj1").resolve("reservoir_levels");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("reservoir_levels_FR_2030.xlsx"));

        when(nasFileService.readMatrix(any(Path.class), any(), anyBoolean(), any(), any())).thenReturn(new TimeSeriesMatrix(List.of()));
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), anyString(), anyString()))
                .thenReturn("FR_reservoir_levels.arrow");

        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").reservoir(true).build();
        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("reservoir_levels_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        HydroGenerationDTO dto = result.get("FR").hydro();
        assertNotNull(dto.getSeries());
        assertEquals(1, dto.getSeries().length);
    }

    @Test
    void assembleHydroProperties_reservoirLevelsFilteredWhenReservoirFalse() {
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").reservoir(false).build();
        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("reservoir_levels_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertNull(result.get("FR").hydro().getSeries());
    }

    @Test
    void assembleHydroProperties_reservoirLevelsFilteredWhenReservoirNull() {
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").reservoir(null).build();
        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("reservoir_levels_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertNull(result.get("FR").hydro().getSeries());
    }

    @Test
    void assembleHydroProperties_othersAreaSetsSeriesForExtractedArea(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj_others").resolve("mingen");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("mingen_DE_2030.xlsx"));

        when(nasFileService.readMatrix(any(Path.class), any(), anyBoolean(), any(), any())).thenReturn(new TimeSeriesMatrix(List.of()));
        when(nasFileService.saveMatrixToNas(any(TimeSeriesMatrix.class), eq("DE_mingen"), eq("hydro_output")))
                .thenReturn("DE_mingen.arrow");

        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("mingen_DE_2030.xlsx").build();
        TrajectoryEntity othersTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("OTHERS")
                .fileName("traj_others")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        HydroParametersEntity hp = HydroParametersEntity.builder().node("DE").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(othersTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertTrue(result.containsKey("DE"));
        HydroGenerationDTO dto = result.get("DE").hydro();
        assertNotNull(dto.getSeries());
        assertArrayEquals(new String[]{"DE_mingen.arrow"}, dto.getSeries());
    }

    @Test
    void assembleHydroProperties_filtersHydroSeriesWithNullTsName() {
        HydroSeriesEntity hydroSeriesNullTs = HydroSeriesEntity.builder().tsName(null).build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeriesNullTs))
                .build();
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertNotNull(result.get("FR"));
        assertNull(result.get("FR").hydro().getSeries());
    }

    @Test
    void assembleHydroProperties_filtersHydroSeriesTrajectoryWithNullArea() {
        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("mingen_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectoryNullArea = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area(null)
                .fileName("traj1")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectoryNullArea))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertTrue(result.isEmpty());
    }

    @Test
    void assembleHydroProperties_returnsGroupedProperties_forPsp() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("FR")
                .reservoirCapacity(new BigDecimal(3000))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertEquals(1, result.size());
        assertNull(result.get("FR").hydro());
        assertEquals(new BigDecimal("3000"), result.get("FR").psp().getProperties().getReservoirCapacity());
    }

    @Test
    void assembleHydroProperties_pspAllocation_virtualNodeHydroColumn_extractsAreaAndUsesLoadAsKey() {
        HydroParametersEntity hp = HydroParametersEntity.builder()
                .node("AT")
                .build();

        HydroAllocationEntity ha = HydroAllocationEntity.builder()
                .hydro("w_hydro_open_at")
                .load("FR")
                .allocation(BigDecimal.valueOf(1))
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .hydroAllocationEntities(List.of(ha))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertTrue(result.containsKey("AT"));
        Map<String, Double> allocation = result.get("AT").psp().getAllocation();
        assertNotNull(allocation);
        assertEquals(1, allocation.size());
        assertTrue(allocation.containsKey("FR"));
        assertEquals(1.0, allocation.get("FR"));
    }

    @Test
    void assembleHydroProperties_setsSeriesForMaxpowerFile_forPsp_readsSpecificColumnsAndAppendsMarker(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path pspDir = tempDir.resolve("trajectories").resolve("psp_series").resolve("traj_psp");
        Files.createDirectories(pspDir.resolve("mingen"));
        Files.createFile(pspDir.resolve("maxpower_2030.xlsx"));
        Files.createFile(pspDir.resolve("mingen").resolve("mingen_FR_2030.xlsx"));

        // PSP area has mingen -> maxpower must contain FR_generating and FR_pumping
        TimeSeriesMatrix maxpowerMatrix = new TimeSeriesMatrix(List.of(
                new TimeSeriesMatrixColumn("FR_generating", new double[0]),
                new TimeSeriesMatrixColumn("FR_pumping", new double[0])
        ));
        when(timeSeriesReader.readSelectedColumnsFromXlsx(any(), any(), any())).thenReturn(maxpowerMatrix);
        when(nasFileService.readMatrix(any(Path.class), any(), anyBoolean(), any(), any())).thenReturn(new TimeSeriesMatrix(List.of()));
        when(nasFileService.saveMatrixToNas(any(), eq("FR_psp_maxpower"), eq("hydro_output"))).thenReturn("FR_psp_maxpower.arrow");
        when(nasFileService.saveMatrixToNas(any(), eq("FR_psp_mingen"), eq("hydro_output"))).thenReturn("FR_psp_mingen.arrow");

        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_PSP_SERIES.name())
                .area("FR")
                .fileName("traj_psp")
                .hydroSeriesEntities(List.of(
                        HydroSeriesEntity.builder().tsName("mingen_FR_2030.xlsx").build(),
                        HydroSeriesEntity.builder().tsName("maxpower_2030.xlsx").build()
                ))
                .build();

        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        verify(timeSeriesReader).readSelectedColumnsFromXlsx(any(), any(), eq(Set.of("FR_generating", "FR_pumping")));
        assertNotNull(result.get("FR").psp().getSeries());
        assertTrue(List.of(result.get("FR").psp().getSeries()).contains("FR_psp_maxpower.arrow"));
        
        when(timeSeriesReader.readSelectedColumnsFromXlsx(any(), any(), any()))
                .thenReturn(new TimeSeriesMatrix(List.of(new TimeSeriesMatrixColumn("FR_generating", new double[0]))));
        assertThrows(BusinessException.class, () -> service.assembleHydroProperties(studyEntity));
    }

    @Test
    void assembleHydroProperties_setsSeriesForNormalFile_forPsp_appendsMarker(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("psp_series").resolve("traj_psp").resolve("mingen");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("mingen_FR_2030.xlsx"));

        when(nasFileService.readMatrix(any(), any(), anyBoolean(), any(), any())).thenReturn(new TimeSeriesMatrix(List.of()));
        when(nasFileService.saveMatrixToNas(any(), eq("FR_psp_mingen"), eq("hydro_output")))
                .thenReturn("FR_psp_mingen.arrow");

        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("mingen_FR_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_PSP_SERIES.name())
                .area("FR")
                .fileName("traj_psp")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();

        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();

        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertNotNull(result.get("FR").psp().getSeries());
        assertArrayEquals(new String[]{"FR_psp_mingen.arrow"}, result.get("FR").psp().getSeries());
    }

    @Test
    void assembleHydroProperties_usesHydroOutputDirectoryForNonPspSeries(@TempDir Path tempDir) throws IOException {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getHydroTsOutputDirectory()).thenReturn("hydro_output");

        Path fileDir = tempDir.resolve("trajectories").resolve("hydro_series").resolve("traj_hydro");
        Files.createDirectories(fileDir);
        Files.createFile(fileDir.resolve("maxpower_2030.xlsx"));

        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of());
        when(timeSeriesReader.readSelectedColumnsFromXlsx(any(), any(), any())).thenReturn(matrix);
        when(nasFileService.saveMatrixToNas(any(), eq("FR_maxpower"), eq("hydro_output")))
                .thenReturn("FR_maxpower.arrow");

        HydroSeriesEntity hydroSeries = HydroSeriesEntity.builder().tsName("maxpower_2030.xlsx").build();
        TrajectoryEntity seriesTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_SERIES.name())
                .area("FR")
                .fileName("traj_hydro")
                .hydroSeriesEntities(List.of(hydroSeries))
                .build();
        HydroParametersEntity hp = HydroParametersEntity.builder().node("FR").build();
        TrajectoryEntity techTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS.name())
                .hydroParametersEntities(List.of(hp))
                .build();
        StudyEntity studyEntity = StudyEntity.builder()
                .trajectories(Set.of(seriesTrajectory, techTrajectory))
                .build();

        Map<String, HydroAreaGenerationDTO> result = service.assembleHydroProperties(studyEntity);

        assertNotNull(result.get("FR").hydro().getSeries());
        assertArrayEquals(new String[]{"FR_maxpower.arrow"}, result.get("FR").hydro().getSeries());
    }
}
