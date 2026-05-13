package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.HydroAllocationRowProcessingResult;
import com.rte_france.antares.datamanager_back.service.hydro.HydroParametersRowProcessingResult;
import com.rte_france.antares.datamanager_back.service.hydro.HydroTechnicalParametersRowProcessingResult;
import com.rte_france.antares.datamanager_back.service.hydro.impl.HydroFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.res.ResRowProcessingContext;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class HydroFileProcessorServiceImplTest {

    private static final String AREA_FR = "FR";
    private static final String HORIZON = "2029-2030";
    private static final String TRAJ = "BP_23";
    private static final String FILE_NAME_MAX_POWER = "maxpower_FR_2029-2030.xlsx";
    private static final String FILE_NAME_MOD = "mod_FR_2029-2030.csv";
    private static final String FILE_NAME_ROR = "ror_FR_2029-2030.csv";
    private static final String FILE_NAME_MINGEN = "mingen_FR_2029-2030.csv";
    private static final String FILE_NAME_RESERVOIR_LEVELS = "reservoir_levels_FR_2029-2030.csv";
    private static final String FILE_NAME_ALLOCATION = "hydroAllocation_BP_23.xlsx";
    private static final String FILE_NAME_PARAMETERS = "hydroParameters_BP_23.xlsx";

    @InjectMocks
    private HydroFileProcessorServiceImpl service;

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private AreaRepository areaRepository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        lenient().when(trajectoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // processHydroSeriesFile
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowWhenNoMaxPowerFileFound() throws Exception {
        Path base = tempDir.resolve("hydro");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj);

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any()))
                .thenReturn(base);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TRAJ, HORIZON, 1, AREA_FR, false)
        );
        assertTrue(exception.getMessage().contains("No maxpower file found"));
    }

    @Test
    void shouldProcessHydroSeriesTrajectorySuccessfully() throws Exception {
        Path base = tempDir.resolve("hydro");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj);
        CreateExcelTestUtil.createExcelFile(traj, FILE_NAME_MAX_POWER, "2029-2030", List.of("areas", "AT", "BE", "FR"),
                List.of(
                        List.of(2345, 2345, 2345, 2345)
                ));
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = traj.resolve("mingen");
        Files.createDirectories(mingenDir);
        Path reservoirLevels = traj.resolve("reservoir_levels");
        Files.createDirectories(reservoirLevels);

        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_ROR);
        CreateExcelTestUtil.createMockCsvFile(mingenDir, FILE_NAME_MINGEN);
        CreateExcelTestUtil.createMockCsvFile(reservoirLevels, FILE_NAME_RESERVOIR_LEVELS);

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any()))
                .thenReturn(base);
        Mockito.when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());

        Mockito.when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        TrajectoryEntity result = service.processHydroSeriesFile(TRAJ, HORIZON, 1, AREA_FR, false);

        assertNotNull(result);
        assertEquals(5, result.getHydroSeriesEntities().size());
    }

    @Test
    void shouldThrowWhenMaxPowerHasNoSelectedArea() throws Exception {
        Path base = tempDir.resolve("hydro");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj);
        CreateExcelTestUtil.createExcelFile(traj, FILE_NAME_MAX_POWER, "2029-2030", List.of("areas", "AT", "BE", "YU"),
                List.of(
                        List.of(2345, 2345, 2345, 2345)
                ));
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = traj.resolve("mingen");
        Files.createDirectories(mingenDir);
        Path reservoirLevels = traj.resolve("reservoir_levels");
        Files.createDirectories(reservoirLevels);

        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_ROR);
        CreateExcelTestUtil.createMockCsvFile(mingenDir, FILE_NAME_MINGEN);
        CreateExcelTestUtil.createMockCsvFile(reservoirLevels, FILE_NAME_RESERVOIR_LEVELS);

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any()))
                .thenReturn(base);
        Mockito.when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());

        Mockito.when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}, new AreaEntity() {{
            setName("AT");
        }}, new AreaEntity() {{
            setName("BE");
        }}, new AreaEntity() {{
            setName("YU");
        }}));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TRAJ, HORIZON, 1, AREA_FR, false)
        );
        assertTrue(exception.getMessage().contains("Selected area {0} is not present in the 'node' column of {1} trajectory {2}"));
    }

    @Test
    void processHydroSeriesFile_throwsBusinessException_whenTrajectoryPathIsOutsideBaseDirectory() throws IOException {
        Path baseDirectory = tempDir.resolve("hydro").resolve("series");
        Files.createDirectories(baseDirectory);
        String maliciousTrajectoryToUse = "../outside";
        Path trajectoryPath = baseDirectory.resolve(maliciousTrajectoryToUse);

        Path inflowDir = trajectoryPath.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = trajectoryPath.resolve("mingen");
        Files.createDirectories(mingenDir);
        Path reservoirLevels = trajectoryPath.resolve("reservoir_levels");
        Files.createDirectories(reservoirLevels);

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(
                eq(TrajectoryType.HYDRO_SERIES),
                eq("FR"),
                isNull()
        )).thenReturn(baseDirectory);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(
                        maliciousTrajectoryToUse,
                        "2030-2031",
                        1,
                        "FR",
                        true
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Invalid trajectory path: ../outside", exception.getMessage());

        verify(trajectoryService).normalizeAndValidateDirectory(
                TrajectoryType.HYDRO_SERIES,
                "FR",
                null
        );
        verifyNoInteractions(trajectoryRepository);
    }

    @Test
    void processHydroSeriesFile_throwsBusinessException_whenSeriesFileNameDoesNotMatchExpectedPattern(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "BP_23";
        String horizon = "2029-2030";
        String area = "FR";
        Integer studyId = 1;

        Path baseDirectory = tempDir.resolve("hydro").resolve("series");
        Path trajectoryPath = baseDirectory.resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);
        CreateExcelTestUtil.createExcelFile(trajectoryPath, FILE_NAME_MAX_POWER, horizon, List.of("areas", "AT", "BE", "FR"),
                List.of(
                        List.of(2345, 2345, 2345, 2345)
                ));
        Path inflowDir = trajectoryPath.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = trajectoryPath.resolve("mingen");
        Files.createDirectories(mingenDir);
        Path reservoirLevels = trajectoryPath.resolve("reservoir_levels");
        Files.createDirectories(reservoirLevels);

        Files.createFile(inflowDir.resolve("ror_FR.csv"));

        when(trajectoryService.normalizeAndValidateDirectory(
                eq(TrajectoryType.HYDRO_SERIES),
                eq(area),
                isNull()
        )).thenReturn(baseDirectory);

        var trajectory = TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .type(TrajectoryType.HYDRO_SERIES.name())
                .horizon(horizon)
                .area(area)
                .build();

        when(trajectoryService.buildDirectoryTrajectory(
                eq(TrajectoryType.HYDRO_SERIES.name()),
                eq(trajectoryToUse),
                eq(trajectoryPath),
                eq(horizon),
                eq(area),
                isNull()
        )).thenReturn(trajectory);

        Mockito.when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}, new AreaEntity() {{
            setName("AT");
        }}, new AreaEntity() {{
            setName("BE");
        }}, new AreaEntity() {{
            setName("FR");
        }}));

        service.processHydroSeriesFile(
                        trajectoryToUse,
                        horizon,
                        studyId,
                        area,
                        true
                );

        verify(trajectoryRepository, times(1)).save(trajectory);
    }

    @Test
    void validateMaxPowerFile_throwsBusinessException_whenFilePathIsNull() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.validateMaxPowerFile(
                        null,
                        TRAJ,
                        HORIZON,
                        AREA_FR,
                        List.of(AREA_FR),
                        TrajectoryType.HYDRO_SERIES
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Max power file path is not valid", exception.getMessage());
    }

    @Test
    void processHydroSeriesFile_throwsBusinessException_whenTrajectoryDirectoryDoesNotExistForMaxPower(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "BP_23";
        String horizon = "2029-2030";
        String area = "FR";
        Integer studyId = 1;

        Path baseDirectory = tempDir.resolve("hydro").resolve("series");
        Files.createDirectories(baseDirectory);

        Path missingTrajectoryPath = baseDirectory.resolve(trajectoryToUse);

        Path inflowDir = missingTrajectoryPath.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = missingTrajectoryPath.resolve("mingen");
        Files.createDirectories(mingenDir);
        Path reservoirLevels = missingTrajectoryPath.resolve("reservoir_levels");
        Files.createDirectories(reservoirLevels);

        when(trajectoryService.normalizeAndValidateDirectory(
                eq(TrajectoryType.HYDRO_SERIES),
                eq(area),
                isNull()
        )).thenReturn(baseDirectory);

        when(trajectoryService.buildDirectoryTrajectory(
                eq(TrajectoryType.HYDRO_SERIES.name()),
                eq(trajectoryToUse),
                eq(missingTrajectoryPath),
                eq(horizon),
                eq(area),
                isNull()
        )).thenReturn(TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .type(TrajectoryType.HYDRO_SERIES.name())
                .horizon(horizon)
                .area(area)
                .build());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(
                        trajectoryToUse,
                        horizon,
                        studyId,
                        area,
                        true
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("No maxpower file found for HYDRO series trajectory.", exception.getMessage());

        verify(trajectoryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // processHydroTechnicalParametersFile
    // -------------------------------------------------------------------------

    @Test
    void processHydroTechnicalParametersFile_happyPath_returnsTrajectoryWithAllEntities() throws Exception {
        Path base = tempDir.resolve("hydro_tech");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj);

        CreateExcelTestUtil.createExcelFile(traj, FILE_NAME_ALLOCATION, HORIZON,
                List.of("hydro", "load", "allocation"),
                List.of(List.of("FR", "FR_LOAD", 100)));

        CreateExcelTestUtil.createExcelFile(traj, FILE_NAME_PARAMETERS, HORIZON,
                List.of("node", "inter.daily.breakdown", "intra.daily.modulation", "inter.monthly.breakdown",
                        "initialize.reservoir.date", "pumping.efficiency",
                        "reservoir", "reservoir.capacity", "follow.load", "use.water"),
                List.of(List.of("FR", 1, 1, 1, 1, 1, true, 1000, false, true)));

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        Mockito.when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        Mockito.when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        TrajectoryEntity result = service.processHydroTechnicalParametersFile(TRAJ, HORIZON, 1, AREA_FR, false);

        assertNotNull(result);
        assertEquals(1, result.getHydroAllocationEntities().size());
        assertEquals(1, result.getHydroParametersEntities().size());
    }

    @Test
    void processHydroTechnicalParametersFile_throwsWhenPathTraversal() throws IOException {
        Path base = tempDir.resolve("hydro_tech_traversal");
        Files.createDirectories(base);

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroTechnicalParametersFile("../outside", HORIZON, 1, AREA_FR, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Invalid trajectory path"));
        verifyNoInteractions(trajectoryRepository);
    }

    @Test
    void processHydroTechnicalParametersFile_throwsWhenAllocationFileMissing() throws Exception {
        Path base = tempDir.resolve("hydro_tech_missing_alloc");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj);

        CreateExcelTestUtil.createExcelFile(traj, FILE_NAME_PARAMETERS, HORIZON,
                List.of("node"), List.of());

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        Mockito.when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroTechnicalParametersFile(TRAJ, HORIZON, 1, AREA_FR, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Missing file hydroAllocation or hydroParameters"));
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void processHydroTechnicalParametersFile_throwsWhenParametersFileMissing() throws Exception {
        Path base = tempDir.resolve("hydro_tech_missing_params");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj);

        CreateExcelTestUtil.createExcelFile(traj, FILE_NAME_ALLOCATION, HORIZON,
                List.of("hydro"), List.of());

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        Mockito.when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroTechnicalParametersFile(TRAJ, HORIZON, 1, AREA_FR, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Missing file hydroAllocation or hydroParameters"));
        verify(trajectoryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // processTechnicalParametersFile
    // -------------------------------------------------------------------------

    @Test
    void processTechnicalParametersFile_happyPath_allocation_returnsEntityList() throws Exception {
        Path dir = tempDir.resolve("alloc_happy");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, "hydroAllocation_test.xlsx", HORIZON,
                List.of("hydro", "load", "allocation"),
                List.of(List.of("FR", "FR_LOAD", 100)));

        HydroTechnicalParametersRowProcessingResult result = service.processTechnicalParametersFile(
                filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION);

        assertNotNull(result);
        assertInstanceOf(HydroAllocationRowProcessingResult.class, result);
        assertEquals(1, ((HydroAllocationRowProcessingResult) result).entities().size());

        var entity = ((HydroAllocationRowProcessingResult) result).entities().getFirst();
        assertEquals("FR", entity.getHydro());
        assertEquals("FR_LOAD", entity.getLoad());
        assertEquals(100, entity.getAllocation());
    }

    @Test
    void processTechnicalParametersFile_happyPath_parameters_returnsEntityList() throws Exception {
        Path dir = tempDir.resolve("params_happy");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, "hydroParameters_test.xlsx", HORIZON,
                List.of("node", "inter.daily.breakdown", "intra.daily.modulation", "inter.monthly.breakdown",
                        "initialize.reservoir.date", "pumping.efficiency",
                        "reservoir", "reservoir.capacity", "follow.load", "use.water"),
                List.of(List.of("FR", 2, 3, 4, 5, 6, true, 1000, false, true)));

        HydroTechnicalParametersRowProcessingResult result = service.processTechnicalParametersFile(
                filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_PARAMETERS);

        assertNotNull(result);
        assertInstanceOf(HydroParametersRowProcessingResult.class, result);
        assertEquals(1, ((HydroParametersRowProcessingResult) result).entities().size());

        var entity = ((HydroParametersRowProcessingResult) result).entities().getFirst();
        assertEquals("FR", entity.getNode());
        assertEquals(2, entity.getInterDailyBreakdown());
        assertEquals(3, entity.getInterDailyModulation());
        assertEquals(Boolean.TRUE, entity.getReservoir());
        assertEquals(Boolean.FALSE, entity.getFollowLoad());
    }

    @Test
    void processTechnicalParametersFile_throwsWhenFilePathIsNull() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processTechnicalParametersFile(
                        null, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void processTechnicalParametersFile_throwsWhenFilePathIsDirectory() throws IOException {
        Path dir = tempDir.resolve("not_a_file");
        Files.createDirectories(dir);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processTechnicalParametersFile(
                        dir, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void processTechnicalParametersFile_throwsWhenMissingAllocationColumns() throws Exception {
        Path dir = tempDir.resolve("missing_alloc_cols");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, "hydroAllocation_test.xlsx", HORIZON,
                List.of("hydro", "load"),
                List.of(List.of("FR", "FR_LOAD")));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processTechnicalParametersFile(
                        filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION));

        assertTrue(exception.getMessage().contains("Missing columns"));
        assertTrue(exception.getErrorMessageArguments().getFirst().contains("allocation"));
    }

    @Test
    void processTechnicalParametersFile_throwsWhenMissingParametersColumns() throws Exception {
        Path dir = tempDir.resolve("missing_params_cols");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, "hydroParameters_test.xlsx", HORIZON,
                List.of("node"),
                List.of(List.of("FR")));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processTechnicalParametersFile(
                        filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_PARAMETERS));

        assertTrue(exception.getMessage().contains("Missing columns"));
    }

    @Test
    void processTechnicalParametersFile_throwsWhenAllRowsAreEmpty() throws Exception {
        Path dir = tempDir.resolve("empty_rows");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, "hydroAllocation_empty.xlsx", HORIZON,
                List.of("hydro", "load", "allocation"),
                List.of());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processTechnicalParametersFile(
                        filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION));

        assertTrue(exception.getMessage().contains("No data found"));
    }

    // -------------------------------------------------------------------------
    // validateMaxPowerFile
    // -------------------------------------------------------------------------

    @Test
    void validateMaxPowerFile_throwsWhenFilePathIsDirectory() throws IOException {
        Path dir = tempDir.resolve("is_a_dir");
        Files.createDirectories(dir);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateMaxPowerFile(dir, TRAJ, HORIZON, AREA_FR, List.of(AREA_FR), TrajectoryType.HYDRO_SERIES));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Max power file path is not valid", exception.getMessage());
    }

    // -------------------------------------------------------------------------
    // validateEmptyRequiredColumns
    // -------------------------------------------------------------------------

    @Test
    void validateEmptyRequiredColumns_doesNotThrowWhenAllValuesAreValidIntegers() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_ALLOCATION);

        assertDoesNotThrow(() ->
                service.validateEmptyRequiredColumns(context, new String[]{"col1", "extra"}, true, "100"));
    }

    @Test
    void validateEmptyRequiredColumns_throwsWhenNumericValueIsNull() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_ALLOCATION);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(context, new String[]{"allocation", "extra"}, true, (Object) null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Allocation column", exception.getErrorMessageArguments().getFirst());
    }

    @Test
    void validateEmptyRequiredColumns_throwsWhenValueIsNonNumericString() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_PARAMETERS);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(context, new String[]{"col1", "extra"}, true, "not_a_number"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Column(s) 2 to 6 an 8", exception.getErrorMessageArguments().getFirst());
    }

    @Test
    void validateEmptyRequiredColumns_throwsWhenBooleanValueIsNull() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_PARAMETERS);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(context, new String[]{"reservoir", "extra"}, false, (Object) null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Column(s) 7, 9 and 10", exception.getErrorMessageArguments().getFirst());
    }

    @Test
    void validateEmptyRequiredColumns_throwsWhenBooleanValueIsNonBooleanString() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_PARAMETERS);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(context, new String[]{"reservoir", "extra"}, false, "maybe"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Column(s) 7, 9 and 10", exception.getErrorMessageArguments().getFirst());
    }

    // -------------------------------------------------------------------------
    // isInteger
    // -------------------------------------------------------------------------

    @Test
    void isInteger_returnsTrueForValidIntegers() {
        assertTrue(HydroFileProcessorServiceImpl.isInteger("42"));
        assertTrue(HydroFileProcessorServiceImpl.isInteger("0"));
        assertTrue(HydroFileProcessorServiceImpl.isInteger("-5"));
        assertTrue(HydroFileProcessorServiceImpl.isInteger("1000"));
    }

    @Test
    void isInteger_returnsFalseForNull() {
        assertFalse(HydroFileProcessorServiceImpl.isInteger(null));
    }

    @Test
    void isInteger_returnsFalseForNonIntegers() {
        assertFalse(HydroFileProcessorServiceImpl.isInteger("1.5"));
        assertFalse(HydroFileProcessorServiceImpl.isInteger("abc"));
        assertFalse(HydroFileProcessorServiceImpl.isInteger(""));
        assertFalse(HydroFileProcessorServiceImpl.isInteger("1e5"));
    }

    // -------------------------------------------------------------------------
    // loadStudyAreas
    // -------------------------------------------------------------------------

    @Test
    void loadStudyAreas_returnsUppercaseAreaNames() {
        Mockito.when(areaRepository.findAllByStudyId(42)).thenReturn(List.of(
                new AreaEntity() {{ setName("fr"); }},
                new AreaEntity() {{ setName("at"); }},
                new AreaEntity() {{ setName("BE"); }}
        ));

        List<String> areas = service.loadStudyAreas(42);

        assertEquals(3, areas.size());
        assertTrue(areas.contains("FR"));
        assertTrue(areas.contains("AT"));
        assertTrue(areas.contains("BE"));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private ResRowProcessingContext buildContext(TrajectoryType type) {
        return ResRowProcessingContext.builder()
                .trajectoryType(type)
                .trajectoryToUse(TRAJ)
                .studyAreas(List.of(AREA_FR))
                .areaParam(AREA_FR)
                .build();
    }
}
