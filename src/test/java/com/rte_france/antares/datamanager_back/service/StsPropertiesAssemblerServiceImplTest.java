package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.StsConstraintParameterDTO;
import com.rte_france.antares.datamanager_back.dto.StsGenerationDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsHoursEntity;
import com.rte_france.antares.datamanager_back.repository.model.StConstraintsParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.StStorageEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.sts.impl.StsPropertiesAssemblerServiceImpl;
import com.rte_france.antares.datamanager_back.service.sts.StsTsFile;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrixColumn;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;


import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StsPropertiesAssemblerServiceImplTest {

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;
    @Mock
    private NasFileService nasFileService;
    @Mock
    private TimeSeriesWriter timeSeriesWriter;
    @Mock
    private TimeSeriesReader timeSeriesReader;
    @TempDir
    Path tempDir;

    @InjectMocks
    private StsPropertiesAssemblerServiceImpl stsPropertiesAssemblerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getStsDirectory()).thenReturn("sts");
        ReflectionTestUtils.setField(stsPropertiesAssemblerService, "antaresDataManagerProperties", antaresDataManagerProperties);
        ReflectionTestUtils.setField(stsPropertiesAssemblerService, "nasFileService", nasFileService);
    }

    @Test
    void assembleStsProperties_ShouldReturnMappedProperties() {
        // Given
        StStorageEntity stStorage1 = StStorageEntity.builder()
                .area("FR")
                .name("Storage1")
                .groupe("Group1")
                .injection(new BigDecimal("10.5"))
                .withdrawal(new BigDecimal("5.2"))
                .storage(new BigDecimal("100.0"))
                .efficiencyInjection(new BigDecimal("0.9"))
                .efficiencyWithdrawal(new java.math.BigDecimal("0.2")) // StStorageEntity has Integer for efficiencyWithdrawal
                .initialLevel(new BigDecimal("0.5"))
                .initialLevelOptim(true)
                .enabled(true)
                .tsPath(null)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage1))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_Storage1"));
        StsGenerationDTO dto = result.get("FR_Storage1");
        assertEquals(true, dto.getEnabled());
        assertEquals("Group1", dto.getGroupe());
        assertEquals(10, dto.getInjection());
        assertEquals(5.2, dto.getWithdrawal());
        assertEquals(100.0, dto.getStorage());
        assertEquals(0.9, dto.getEfficiencyInjection());
        assertEquals(0.2, dto.getEfficiencyWithdrawal());
        assertEquals(0.5, dto.getInitialLevel());
        assertEquals(true, dto.getInitialLevelOptim());
    }

    @Test
    void assembleStsProperties_ShouldHandleMultipleTrajectoriesAndAreas() {
        // Given
        StStorageEntity stStorage1 = StStorageEntity.builder()
                .area("fr")
                .name("S1")
                .enabled(true)
                .injection(BigDecimal.ONE)
                .tsPath("")
                .build();
        TrajectoryEntity traj1 = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage1))
                .build();

        StStorageEntity stStorage2 = StStorageEntity.builder()
                .area("be")
                .name("S2")
                .enabled(false)
                .withdrawal(BigDecimal.ONE)
                .tsPath("")
                .build();
        TrajectoryEntity traj2 = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage2))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(traj1, traj2))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.containsKey("FR_S1"));
        assertTrue(result.containsKey("BE_S2"));
        assertEquals(true, result.get("FR_S1").getEnabled());
        assertEquals(false, result.get("BE_S2").getEnabled());
    }

    @Test
    void assembleStsProperties_ShouldSkipNonStsTrajectories() {
        // Given
        TrajectoryEntity traj1 = TrajectoryEntity.builder()
                .type(TrajectoryType.AREA.name())
                .stStorageEntities(List.of(StStorageEntity.builder().area("FR").name("S1").build()))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(traj1))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void assembleStsProperties_ShouldHandleNullFields() {
        // Given
        StStorageEntity stStorage = StStorageEntity.builder()
                .area("FR")
                .name("S1")
                .storage(BigDecimal.TEN)
                .tsPath(null)
                .build();
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorage))
                .build();
        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        StsGenerationDTO dto = result.get("FR_S1");
        assertEquals(false, dto.getEnabled());
        assertEquals(0, dto.getInjection());
        assertEquals(0.0, dto.getWithdrawal());
        assertEquals(false, dto.getInitialLevelOptim());
    }

    @Test
    void assembleStsProperties_ShouldExcludeClustersWithZeroTotalCapacity() {
        // Given
        StStorageEntity stStorageZero = StStorageEntity.builder()
                .area("FR")
                .name("ZeroCapacity")
                .injection(BigDecimal.ZERO)
                .withdrawal(BigDecimal.ZERO)
                .storage(BigDecimal.ZERO)
                .tsPath("")
                .build();

        StStorageEntity stStoragePartial = StStorageEntity.builder()
                .area("FR")
                .name("PartialCapacity")
                .injection(BigDecimal.ZERO)
                .withdrawal(new BigDecimal("1.0"))
                .storage(BigDecimal.ZERO)
                .tsPath("")
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(stStorageZero, stStoragePartial))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.containsKey("FR_PartialCapacity"));
        assertFalse(result.containsKey("FR_ZeroCapacity"));
    }

    @Test
    void shouldReturnEmptyListWhenEntityIsNull() {
        List<String> result = stsPropertiesAssemblerService.createMatrixStsTsFiles(null, "2030");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenTsPathIsBlank() {
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath("   ");

        List<String> result = stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, "2030");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSaveAllFilesWhenAllRequiredFilesExist() throws Exception {
        // given
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath(tempDir.toString());
        String horizon = "2030";

        when(antaresDataManagerProperties.getStsTsOutputDirectory()).thenReturn("/output");
        when(nasFileService.getWriter()).thenReturn(timeSeriesWriter);
        when(timeSeriesWriter.writeToByteArray(any())).thenReturn(new byte[]{1, 2, 3});

        for (StsTsFile file : StsTsFile.values()) {
            Files.createFile(file.resolve(tempDir));
        }
        when(nasFileService.readMatrix(any(Path.class), eq(horizon))).thenReturn(mock(TimeSeriesMatrix.class));
        when(nasFileService.saveMatrixBytesToNas(any(), any(), eq("/output"))).thenReturn("saved.csv");

        // when
        List<String> result = stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, horizon);

        // then
        assertEquals(5, result.size());
    }

    @Test
    void shouldThrowBusinessExceptionWhenARequiredFileIsMissing() throws Exception {
        // given
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath(tempDir.toString());

        when(antaresDataManagerProperties.getStsTsOutputDirectory()).thenReturn("/output");

        // create all files except one
        StsTsFile missing = StsTsFile.INFLOWS;

        for (StsTsFile file : StsTsFile.values()) {
            if (file != missing) {
                Files.createFile(file.resolve(tempDir));
            }
        }

        // when / then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, "2030")
        );

        assertTrue(ex.getMessage().contains("Required STS series file not found"));
        assertTrue(ex.getErrorMessageArguments().contains(missing.resolve(tempDir).toString()));

        verifyNoInteractions(nasFileService);
    }

    @Test
    void shouldThrowBusinessExceptionOnIOException() throws Exception {
        // given
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath(tempDir.toString());

        when(antaresDataManagerProperties.getStsTsOutputDirectory()).thenReturn("/output");

        for (StsTsFile file : StsTsFile.values()) {
            Files.createFile(file.resolve(tempDir));
        }

        when(nasFileService.readMatrix(any(Path.class), any()))
                .thenThrow(new RuntimeException("NAS error"));

        // when / then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, "2030")
        );

        assertTrue(ex.getMessage().contains("NAS error"));
    }

    @Test
    void shouldPropagateBusinessExceptionWhenSheetNotFound() throws Exception {
        // given
        StStorageEntity entity = new StStorageEntity();
        entity.setTsPath(tempDir.toString());
        String horizon = "2030";

        when(antaresDataManagerProperties.getStsTsOutputDirectory()).thenReturn("/output");

        for (StsTsFile file : StsTsFile.values()) {
            Files.createFile(file.resolve(tempDir));
        }

        BusinessException originalEx = BusinessException.builder()
                .message("Horizon {0} does not exist in file: {1}")
                .errorMessageArguments(List.of(horizon, "inflows.xlsx"))
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build();

        when(nasFileService.readMatrix(any(Path.class), eq(horizon))).thenThrow(originalEx);

        // when / then
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> stsPropertiesAssemblerService.createMatrixStsTsFiles(entity, horizon)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals("Horizon {0} does not exist in file: {1}", ex.getMessage());
        assertEquals(2, ex.getErrorMessageArguments().size());
        assertEquals(horizon, ex.getErrorMessageArguments().getFirst());
    }

    @Test
    void assembleStsProperties_ShouldPopulateConstraintParameters() throws Exception {
        // Given — constraint xlsx file must exist so buildStorageConstraintsContext doesn't skip it
        Path stsDir = tempDir.resolve("trajectories").resolve("sts");
        Files.createDirectories(stsDir);
        Files.createFile(stsDir.resolve("Additional-constraints.xlsx"));

        StConstraintsHoursEntity hour1 = StConstraintsHoursEntity.builder()
                .occurrence(1).startHour(8).endHour(20).build();
        StConstraintsHoursEntity hour2 = StConstraintsHoursEntity.builder()
                .occurrence(2).startHour(0).endHour(6).build();

        StConstraintsParameterEntity param = StConstraintsParameterEntity.builder()
                .name("daily_min_ev_fr")
                .zone("FR")
                .variable("injection")
                .operator("greater")
                .enabled(true)
                .hours(List.of(hour1, hour2))
                .build();

        StStorageEntity storage = StStorageEntity.builder()
                .id(1)
                .area("FR")
                .name("ev_FR")
                .injection(new BigDecimal("100"))
                .constraintsFlag(true)
                .constraintsPath("Additional-constraints.xlsx")
                .parameters(List.of(param))
                .tsPath(null)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .area("FR")
                .stStorageEntities(List.of(storage))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // Mock constraints file processing: return a matrix with one column matching the param name
        TimeSeriesMatrix matrix = new TimeSeriesMatrix(
                List.of(new TimeSeriesMatrixColumn("daily_min_ev_fr", new double[]{1.0})));
        when(timeSeriesReader.readFromXlsx(any(Path.class), any())).thenReturn(matrix);
        when(antaresDataManagerProperties.getStsTsOutputDirectory()).thenReturn("/output");
        when(nasFileService.getWriter()).thenReturn(timeSeriesWriter);
        when(timeSeriesWriter.writeToByteArray(any())).thenReturn(new byte[]{1});
        when(nasFileService.saveMatrixBytesToNas(any(), eq("daily_min_ev_fr.csv"), any()))
                .thenReturn("daily_min_ev_fr.csv.abc123.arrow");

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        StsGenerationDTO dto = result.get("FR_ev_FR");
        assertNotNull(dto.getConstraintParameters());
        assertEquals(1, dto.getConstraintParameters().size());

        StsConstraintParameterDTO constraintParam = dto.getConstraintParameters().get("daily_min_ev_fr");
        assertNotNull(constraintParam);
        assertEquals("injection", constraintParam.getVariable());
        assertEquals("greater", constraintParam.getOperator());
        assertEquals("true", constraintParam.getEnabled());
        assertEquals(List.of(List.of(1, 8, 20), List.of(2, 0, 6)), constraintParam.getHours());
    }

    @Test
    void assembleStsProperties_ShouldHaveNullConstraintParametersWhenConstraintsFlagFalse() {
        // Given — constraintsFlag not set: entity is eligible (non-zero capacity) but has no constraints
        StStorageEntity storage = StStorageEntity.builder()
                .id(1)
                .area("FR")
                .name("S1")
                .injection(new BigDecimal("100"))
                .constraintsFlag(false)
                .parameters(null)
                .tsPath(null)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(storage))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        assertNull(result.get("FR_S1").getConstraintParameters());
    }

    @Test
    void assembleStsProperties_ShouldPopulateConstraintParameters_WhenNameHasNoAreaSuffix() throws Exception {
        // Reproduces the bug: param name "daily_min" has no "_fr" suffix, but zone="FR" → must still be included
        Path stsDir = tempDir.resolve("trajectories").resolve("sts");
        Files.createDirectories(stsDir);
        Files.createFile(stsDir.resolve("Additional-constraints.xlsx"));

        StConstraintsParameterEntity param = StConstraintsParameterEntity.builder()
                .name("daily_min")      // no area suffix — this is the key difference
                .zone("FR")             // zone is the actual area identifier
                .variable("injection")
                .operator("greater")
                .enabled(true)
                .hours(List.of(StConstraintsHoursEntity.builder().occurrence(1).startHour(8).endHour(20).build()))
                .build();

        StStorageEntity storage = StStorageEntity.builder()
                .id(1)
                .area("FR")
                .name("ev_FR")
                .injection(new BigDecimal("100"))
                .constraintsFlag(true)
                .constraintsPath("Additional-constraints.xlsx")
                .parameters(List.of(param))
                .tsPath(null)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .area("FR")
                .stStorageEntities(List.of(storage))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        TimeSeriesMatrix matrix = new TimeSeriesMatrix(
                List.of(new TimeSeriesMatrixColumn("daily_min", new double[]{1.0})));
        when(timeSeriesReader.readFromXlsx(any(Path.class), any())).thenReturn(matrix);
        when(antaresDataManagerProperties.getStsTsOutputDirectory()).thenReturn("/output");
        when(nasFileService.getWriter()).thenReturn(timeSeriesWriter);
        when(timeSeriesWriter.writeToByteArray(any())).thenReturn(new byte[]{1});
        when(nasFileService.saveMatrixBytesToNas(any(), any(), any())).thenReturn("daily_min.csv.abc.arrow");

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        StsGenerationDTO dto = result.get("FR_ev_FR");
        assertNotNull(dto.getConstraintParameters(), "constraintParameters must not be null even when name has no area suffix");
        StsConstraintParameterDTO constraintParam = dto.getConstraintParameters().get("daily_min");
        assertNotNull(constraintParam);
        assertEquals("injection", constraintParam.getVariable());
        assertEquals(List.of(List.of(1, 8, 20)), constraintParam.getHours());
    }

    @Test
    void assembleStsProperties_ShouldMapMultipleConstraintParameters() throws Exception {
        // Given
        Path stsDir = tempDir.resolve("trajectories").resolve("sts");
        Files.createDirectories(stsDir);
        Files.createFile(stsDir.resolve("Additional-constraints.xlsx"));

        StConstraintsParameterEntity param1 = StConstraintsParameterEntity.builder()
                .name("daily_min_be")
                .zone("BE")
                .variable("injection")
                .operator("greater")
                .enabled(true)
                .hours(List.of(
                        StConstraintsHoursEntity.builder().occurrence(1).startHour(0).endHour(8).build()
                ))
                .build();

        StConstraintsParameterEntity param2 = StConstraintsParameterEntity.builder()
                .name("night_min_be")
                .zone("BE")
                .variable("withdrawal")
                .operator("less")
                .enabled(false)
                .hours(List.of())
                .build();

        StStorageEntity storage = StStorageEntity.builder()
                .id(2)
                .area("BE")
                .name("bat")
                .withdrawal(new BigDecimal("50"))
                .constraintsFlag(true)
                .constraintsPath("Additional-constraints.xlsx")
                .parameters(List.of(param1, param2))
                .tsPath(null)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .area("BE")
                .stStorageEntities(List.of(storage))
                .build();

        StudyEntity study = StudyEntity.builder()
                .trajectories(Set.of(trajectory))
                .build();

        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(
                new TimeSeriesMatrixColumn("daily_min_be", new double[]{1.0}),
                new TimeSeriesMatrixColumn("night_min_be", new double[]{2.0})
        ));
        when(timeSeriesReader.readFromXlsx(any(Path.class), any())).thenReturn(matrix);
        when(antaresDataManagerProperties.getStsTsOutputDirectory()).thenReturn("/output");
        when(nasFileService.getWriter()).thenReturn(timeSeriesWriter);
        when(timeSeriesWriter.writeToByteArray(any())).thenReturn(new byte[]{1});
        when(nasFileService.saveMatrixBytesToNas(any(), eq("daily_min_be.csv"), any()))
                .thenReturn("daKeep first-match semantics (equivalent to previous stream().findFirst()).ily_min_be.csv.abc.arrow");
        when(nasFileService.saveMatrixBytesToNas(any(), eq("night_min_be.csv"), any()))
                .thenReturn("night_min_be.csv.def.arrow");

        // When
        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        // Then
        Map<String, StsConstraintParameterDTO> params = result.get("BE_bat").getConstraintParameters();
        assertNotNull(params);
        assertEquals(2, params.size());
        assertEquals("injection", params.get("daily_min_be").getVariable());
        assertEquals("greater", params.get("daily_min_be").getOperator());
        assertEquals(List.of(List.of(1, 0, 8)), params.get("daily_min_be").getHours());
        assertEquals("withdrawal", params.get("night_min_be").getVariable());
        assertEquals("false", params.get("night_min_be").getEnabled());
        assertNull(params.get("night_min_be").getHours());
    }

    @Test
    void assembleStsProperties_ShouldReadSeriesFilesOnceForSharedTsPathAcrossEntities() throws Exception {
        Path sharedTsDir = tempDir.resolve("shared-ts");
        Files.createDirectories(sharedTsDir);
        for (StsTsFile file : StsTsFile.requiredFiles()) {
            Files.createFile(file.resolve(sharedTsDir));
        }

        String horizon = "2029-2030";
        when(antaresDataManagerProperties.getStsTsOutputDirectory()).thenReturn("/output");
        when(nasFileService.getWriter()).thenReturn(timeSeriesWriter);
        when(timeSeriesWriter.writeToByteArray(any())).thenReturn(new byte[]{1});
        when(nasFileService.readMatrix(any(Path.class), eq(horizon))).thenReturn(mock(TimeSeriesMatrix.class));
        when(nasFileService.saveMatrixBytesToNas(any(), any(), eq("/output"))).thenReturn("saved.arrow");

        StStorageEntity first = StStorageEntity.builder()
                .area("FR")
                .name("S1")
                .injection(BigDecimal.ONE)
                .tsPath(sharedTsDir.toString())
                .build();
        StStorageEntity second = StStorageEntity.builder()
                .area("FR")
                .name("S2")
                .injection(BigDecimal.ONE)
                .tsPath(sharedTsDir.toString())
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .stStorageEntities(List.of(first, second))
                .build();

        StudyEntity study = StudyEntity.builder()
                .horizon(horizon)
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        assertEquals(2, result.size());
        verify(nasFileService, times(StsTsFile.requiredFiles().size())).readMatrix(any(Path.class), eq(horizon));
    }

    @Test
    void readConstraintsMatrix_ShouldRejectNonXlsxFile() {
        Path nonXlsx = tempDir.resolve("constraints.csv");
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        stsPropertiesAssemblerService,
                        "readConstraintsMatrix",
                        nonXlsx,
                        "2030"
                )
        );

        assertEquals("Only .xlsx supported", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void readConstraintsMatrix_ShouldThrowTechnicalExceptionWhenMatrixIsEmpty() throws Exception {
        when(timeSeriesReader.readFromXlsx(any(Path.class), anyString()))
                .thenReturn(new TimeSeriesMatrix(List.of()));
        Path additionalConstraints = tempDir.resolve("Additional-constraints.xlsx");

        TechnicalException ex = assertThrows(
                TechnicalException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        stsPropertiesAssemblerService,
                        "readConstraintsMatrix",
                        additionalConstraints,
                        "2030"
                )
        );

        assertTrue(ex.getMessage().contains("Matrix is empty"));
    }

    @Test
    void assembleStsProperties_ShouldSkipConstraintContextWhenFileDoesNotExist() {
        StConstraintsParameterEntity param = StConstraintsParameterEntity.builder()
                .name("daily_min_fr")
                .zone("FR")
                .variable("injection")
                .operator(">")
                .enabled(true)
                .build();

        StStorageEntity storage = StStorageEntity.builder()
                .id(11)
                .area("FR")
                .name("S1")
                .injection(BigDecimal.ONE)
                .constraintsFlag(true)
                .constraintsPath("missing/Additional-constraints.xlsx")
                .parameters(List.of(param))
                .tsPath(null)
                .build();

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .area("FR")
                .stStorageEntities(List.of(storage))
                .build();

        StudyEntity study = StudyEntity.builder()
                .horizon("2029-2030")
                .trajectories(Set.of(trajectory))
                .build();

        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        assertNull(result.get("FR_S1").getConstraintParameters());
        assertTrue(result.get("FR_S1").getStsConstraintsSeriesList().isEmpty());
    }

    @Test
    void mapConstraintParametersFromContext_ShouldIgnoreNullNameNullZoneAndMismatchedZone() {
        StConstraintsParameterEntity nullName = StConstraintsParameterEntity.builder()
                .name(null)
                .zone("FR")
                .build();
        StConstraintsParameterEntity nullZone = StConstraintsParameterEntity.builder()
                .name("daily_min")
                .zone(null)
                .build();
        StConstraintsParameterEntity wrongZone = StConstraintsParameterEntity.builder()
                .name("daily_max")
                .zone("IT")
                .build();

        StStorageEntity storage = StStorageEntity.builder()
                .id(99)
                .parameters(List.of(nullName, nullZone, wrongZone))
                .build();

        StsPropertiesAssemblerServiceImpl.StorageConstraintsContext context =
                new StsPropertiesAssemblerServiceImpl.StorageConstraintsContext(
                        storage,
                        tempDir.resolve("Additional-constraints.xlsx"),
                        Set.of("daily_min", "daily_max"),
                        "FR"
                );

        Map<String, StsConstraintParameterDTO> result =
                ReflectionTestUtils.invokeMethod(
                        stsPropertiesAssemblerService,
                        "mapConstraintParametersFromContext",
                        context,
                        Set.of("FR")
                );

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void assembleStsProperties_ShouldReadConstraintsWorkbookOnceWhenContextsShareSameFile() throws Exception {
        Path stsDir = tempDir.resolve("trajectories").resolve("sts");
        Files.createDirectories(stsDir);
        Path constraintsFile = stsDir.resolve("Additional-constraints.xlsx");
        Files.createFile(constraintsFile);

        StConstraintsParameterEntity frParam = StConstraintsParameterEntity.builder()
                .name("daily_min_fr")
                .zone("FR")
                .variable("injection")
                .operator(">")
                .enabled(true)
                .build();
        StConstraintsParameterEntity beParam = StConstraintsParameterEntity.builder()
                .name("daily_min_be")
                .zone("BE")
                .variable("withdrawal")
                .operator("<")
                .enabled(true)
                .build();

        StStorageEntity frStorage = StStorageEntity.builder()
                .id(21)
                .area("FR")
                .name("S_FR")
                .injection(BigDecimal.ONE)
                .constraintsFlag(true)
                .constraintsPath("Additional-constraints.xlsx")
                .parameters(List.of(frParam))
                .build();

        StStorageEntity beStorage = StStorageEntity.builder()
                .id(22)
                .area("BE")
                .name("S_BE")
                .injection(BigDecimal.ONE)
                .constraintsFlag(true)
                .constraintsPath("Additional-constraints.xlsx")
                .parameters(List.of(beParam))
                .build();

        TrajectoryEntity frTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .area("FR")
                .stStorageEntities(List.of(frStorage))
                .build();
        TrajectoryEntity beTrajectory = TrajectoryEntity.builder()
                .type(TrajectoryType.STS.name())
                .area("BE")
                .stStorageEntities(List.of(beStorage))
                .build();

        StudyEntity study = StudyEntity.builder()
                .horizon("2029-2030")
                .trajectories(Set.of(frTrajectory, beTrajectory))
                .build();

        TimeSeriesMatrix matrix = new TimeSeriesMatrix(List.of(
                new TimeSeriesMatrixColumn("daily_min_fr", new double[]{1.0}),
                new TimeSeriesMatrixColumn("daily_min_be", new double[]{2.0})
        ));

        when(timeSeriesReader.readFromXlsx(constraintsFile, "2029-2030")).thenReturn(matrix);
        when(antaresDataManagerProperties.getStsTsOutputDirectory()).thenReturn("/output");
        when(nasFileService.getWriter()).thenReturn(timeSeriesWriter);
        when(timeSeriesWriter.writeToByteArray(any())).thenReturn(new byte[]{1});
        when(nasFileService.saveMatrixBytesToNas(any(), any(), eq("/output"))).thenReturn("saved.arrow");

        Map<String, StsGenerationDTO> result = stsPropertiesAssemblerService.assembleStsProperties(study);

        assertEquals(2, result.size());
        verify(timeSeriesReader, times(1)).readFromXlsx(constraintsFile, "2029-2030");
        verify(nasFileService, times(2)).saveMatrixBytesToNas(any(), any(), eq("/output"));
    }
}
