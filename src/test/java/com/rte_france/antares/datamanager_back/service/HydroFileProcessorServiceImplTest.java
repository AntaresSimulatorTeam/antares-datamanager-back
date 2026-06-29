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
import com.rte_france.antares.datamanager_back.service.hydro.HydroCoherenceCheckService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroTechnicalParametersRowProcessingResult;
import com.rte_france.antares.datamanager_back.service.hydro.impl.HydroFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.impl.HydroFileProcessorServiceImpl.TechnicalParametersProcessingContext;
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

import static com.rte_france.antares.datamanager_back.util.Utils.OTHERS_AREA;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class HydroFileProcessorServiceImplTest {

    private static final String AREA_FR = "FR";
    private static final String HORIZON = "2029-2030";
    private static final String TRAJ = "BP_23";
    private static final String FILE_NAME_MAX_POWER = "maxpower_BP_23.xlsx";
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

    @Mock
    private HydroCoherenceCheckService hydroCoherenceCheckService;

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
        // processRequiredSeries appelle toRealPath() sur chaque répertoire série → ils doivent exister.
        Path inflowDir = traj.resolve("inflows");
        Path mingenDir = traj.resolve("mingen");
        Path reservoirDir = traj.resolve("reservoir_levels");
        Files.createDirectories(inflowDir);
        Files.createDirectories(mingenDir);
        Files.createDirectories(reservoirDir);
        // mingen + reservoir_levels -> maxpower required (absent = exception).
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);
        CreateExcelTestUtil.createMockCsvFile(mingenDir, FILE_NAME_MINGEN);
        CreateExcelTestUtil.createMockCsvFile(reservoirDir, FILE_NAME_RESERVOIR_LEVELS);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any()))
                .thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, AREA_FR, false)
        );
        assertTrue(exception.getMessage().contains("Missing maxpower file (maxpower_{0}) in Hydro Series trajectory {0}"));
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

        TrajectoryEntity result = service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, AREA_FR, false);

        assertNotNull(result);
        assertEquals(5, result.getHydroSeriesEntities().size());
    }

    @Test
    void shouldProcessHydroSeriesTrajectorySuccessfully_whenAreaIsOthers() throws Exception {
        Path base = tempDir.resolve("hydro_others");
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

        TrajectoryEntity result = service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, OTHERS_AREA, false);

        assertNotNull(result);
        assertEquals(5, result.getHydroSeriesEntities().size());
    }

    @Test
    void shouldExcludeSeriesFilesForAreasNotInStudyAreas_whenAreaIsOthers() throws Exception {
        Path base = tempDir.resolve("hydro_others_exclude");
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

        // FR est dans studyAreas → inclus ; DE n'est pas dans studyAreas → exclu
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_ROR);
        CreateExcelTestUtil.createMockCsvFile(inflowDir, "mod_DE_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(mingenDir, FILE_NAME_MINGEN);
        CreateExcelTestUtil.createMockCsvFile(reservoirLevels, FILE_NAME_RESERVOIR_LEVELS);

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any()))
                .thenReturn(base);
        Mockito.when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());

        Mockito.when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        TrajectoryEntity result = service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, OTHERS_AREA, false);

        assertNotNull(result);
        // 1 maxpower + 4 series FR (mod_DE exclu car DE pas dans studyAreas)
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

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any()))
                .thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());

        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}, new AreaEntity() {{
            setName("AT");
        }}, new AreaEntity() {{
            setName("BE");
        }}, new AreaEntity() {{
            setName("YU");
        }}));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, AREA_FR, false)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        // validateTrajectoryAreasPresence est levée : aucune area du fichier (AT/BE/YU) ne correspond à l'area mod (FR).
        assertTrue(exception.getMessage().contains("Selected area"));
        assertTrue(exception.getErrorMessageArguments().contains(TRAJ));
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
                        TrajectoryType.HYDRO_SERIES,
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
    void processHydroSeriesFile_throwsBusinessException_whenReservoirLevelsFolderIsMissing() throws IOException {
        Path baseDirectory = tempDir.resolve("hydro").resolve("series");
        Path trajectoryPath = baseDirectory.resolve(TRAJ);
        Files.createDirectories(trajectoryPath);

        Path inflowDir = trajectoryPath.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = trajectoryPath.resolve("mingen");
        Files.createDirectories(mingenDir);

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(
                eq(TrajectoryType.HYDRO_SERIES),
                eq("FR"),
                isNull()
        )).thenReturn(baseDirectory);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(
                        TrajectoryType.HYDRO_SERIES,
                        TRAJ,
                        "2030-2031",
                        1,
                        "FR",
                        true
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Missing folder {0} in Hydro Series trajectory {1}", exception.getMessage());

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

        // Fichier au format invalide (sans horizon) → ignoré par le filtre regex.
        Files.createFile(inflowDir.resolve("ror_FR.csv"));
        // Fichier valide : sera pris en compte. Avec uniquement des fichiers ROR, pas de maxpower requis.
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_ROR);

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
                        TrajectoryType.HYDRO_SERIES,
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
                        TrajectoryType.HYDRO_SERIES,
                        null,
                        "TRAJ",
                        "HORIZON",
                        "AREA_FR",
                        List.of("AREA_FR"),
                        List.of()
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Missing maxpower file in Hydro Series trajectory", exception.getMessage());
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
        // mingen + reservoir_levels -> maxpower required but missing
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);
        CreateExcelTestUtil.createMockCsvFile(mingenDir, FILE_NAME_MINGEN);
        CreateExcelTestUtil.createMockCsvFile(reservoirLevels, FILE_NAME_RESERVOIR_LEVELS);

        when(trajectoryService.normalizeAndValidateDirectory(
                eq(TrajectoryType.HYDRO_SERIES),
                eq(area),
                isNull()
        )).thenReturn(baseDirectory);

        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .type(TrajectoryType.HYDRO_SERIES.name())
                .horizon(horizon)
                .area(area)
                .build());

        when(areaRepository.findAllByStudyId(studyId)).thenReturn(List.of(new AreaEntity() {{
            setName(area);
        }}));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(
                        TrajectoryType.HYDRO_SERIES,
                        trajectoryToUse,
                        horizon,
                        studyId,
                        area,
                        true
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Missing maxpower file (maxpower_{0}) in Hydro Series trajectory {0}", exception.getMessage());

        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void processHydroSeriesFile_throwsWhenAllSeriesDirectoriesAreEmpty() throws Exception {
        // Ligne 127 : filesNameFinal.isEmpty() → "Missing files in trajectory Hydro Series trajectory"
        // Aucun fichier dans les répertoires → hasOnlyRorFile reste true (checkHydroFileConsistency
        // retourne true pour une liste vide), le check maxpower est sauté, filesNameFinal reste vide.
        Path base = tempDir.resolve("hydro_empty_dirs");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj.resolve("inflows"));
        Files.createDirectories(traj.resolve("mingen"));
        Files.createDirectories(traj.resolve("reservoir_levels"));

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, AREA_FR, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Missing files in Hydro Series trajectory", exception.getMessage());
    }

    @Test
    void processHydroSeriesFile_throwsWhenMingenPresentButNoModFile() throws Exception {
        // Ligne 368 : hasMingen=true && hasMod=false → "Missing MOD file in trajectory Hydro Series trajectory"
        Path base = tempDir.resolve("hydro_mingen_no_mod");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj.resolve("inflows"));
        Path mingenDir = traj.resolve("mingen");
        Files.createDirectories(mingenDir);
        Files.createDirectories(traj.resolve("reservoir_levels"));
        CreateExcelTestUtil.createMockCsvFile(mingenDir, FILE_NAME_MINGEN);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, AREA_FR, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Missing mod file ({0}) in Hydro Series trajectory {1}"));
    }

    @Test
    void processHydroSeriesFile_throwsWhenReservoirLevelsPresentButNoModFile() throws Exception {
        // Ligne 368 : hasReservoirLevels=true && hasMod=false → "Missing MOD file in trajectory Hydro Series trajectory"
        Path base = tempDir.resolve("hydro_reservoir_no_mod");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj.resolve("inflows"));
        Files.createDirectories(traj.resolve("mingen"));
        Path reservoirDir = traj.resolve("reservoir_levels");
        Files.createDirectories(reservoirDir);
        CreateExcelTestUtil.createMockCsvFile(reservoirDir, FILE_NAME_RESERVOIR_LEVELS);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, AREA_FR, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Missing mod file ({0}) in Hydro Series trajectory {1}"));
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

        TrajectoryEntity result = service.processHydroTechnicalParametersFile(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS, TRAJ, HORIZON, 1, AREA_FR, false);

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
                service.processHydroTechnicalParametersFile(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS, "../outside", HORIZON, 1, AREA_FR, false));

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
                service.processHydroTechnicalParametersFile(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS, TRAJ, HORIZON, 1, AREA_FR, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        String formattedMessage = java.text.MessageFormat.format(exception.getMessage(), exception.getErrorMessageArguments().toArray());
        assertTrue(formattedMessage.contains("Missing file hydroAllocation or hydroParameters"));
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
                service.processHydroTechnicalParametersFile(TrajectoryType.HYDRO_TECHNICAL_PARAMETERS, TRAJ, HORIZON, 1, AREA_FR, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        String formattedMessage = java.text.MessageFormat.format(exception.getMessage(), exception.getErrorMessageArguments().toArray());
        assertTrue(formattedMessage.contains("Missing file hydroAllocation or hydroParameters"));
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void processHydroTechnicalParametersFile_throwsWithPspLabelWhenSelectedAreaMissingFromParameters() throws Exception {
        Path base = tempDir.resolve("hydro_tech_psp_area_mismatch");
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

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroTechnicalParametersFile(TrajectoryType.HYDRO_PSP_TECHNICAL_PARAMETERS, TRAJ, HORIZON, 1, "DE", false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        String formattedMessage = java.text.MessageFormat.format(exception.getMessage(), exception.getErrorMessageArguments().toArray());
        assertTrue(formattedMessage.contains("Selected area"));
        assertTrue(formattedMessage.contains("PSP_Virtual hydroParameters TechnicalParameters"));
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
                new TechnicalParametersProcessingContext(filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION, null, null));

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
                new TechnicalParametersProcessingContext(filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_PARAMETERS, null, null));

        assertNotNull(result);
        assertInstanceOf(HydroParametersRowProcessingResult.class, result);
        assertEquals(1, ((HydroParametersRowProcessingResult) result).entities().size());

        var entity = ((HydroParametersRowProcessingResult) result).entities().getFirst();
        assertEquals("FR", entity.getNode());
        assertEquals(new BigDecimal("2"), entity.getInterDailyBreakdown());
        assertEquals(new BigDecimal("3"), entity.getInterDailyModulation());
        assertEquals(Boolean.TRUE, entity.getReservoir());
        assertEquals(Boolean.FALSE, entity.getFollowLoad());
    }

    @Test
    void processTechnicalParametersFile_throwsWhenFilePathIsNull() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processTechnicalParametersFile(
                        new TechnicalParametersProcessingContext(null, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void processTechnicalParametersFile_throwsWhenFilePathIsDirectory() throws IOException {
        Path dir = tempDir.resolve("not_a_file");
        Files.createDirectories(dir);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processTechnicalParametersFile(
                        new TechnicalParametersProcessingContext(dir, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION, null, null)));

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
                        new TechnicalParametersProcessingContext(filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION, null, null)));

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
                        new TechnicalParametersProcessingContext(filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_PARAMETERS, null, null)));

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
                        new TechnicalParametersProcessingContext(filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION, null, null)));

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
                service.validateMaxPowerFile(TrajectoryType.HYDRO_SERIES, dir, TRAJ, HORIZON, AREA_FR, List.of(AREA_FR), List.of()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Missing maxpower file in Hydro Series trajectory", exception.getMessage());
    }

    @Test
    void validateMaxPowerFile_throwsWhenOthersAreaHasModAreaMissingFromFile() throws Exception {
        Path dir = tempDir.resolve("maxpower_others_missing");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, FILE_NAME_MAX_POWER, HORIZON,
                List.of("areas", "FR", "AT"),
                List.of(List.of(100, 200, 300)));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateMaxPowerFile(
                        TrajectoryType.HYDRO_SERIES,
                        filePath,
                        TRAJ,
                        HORIZON,
                        OTHERS_AREA,
                        List.of("FR", "AT"),
                        List.of("BE")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Area {0} is not present in the 'node' column"));
        assertTrue(exception.getErrorMessageArguments().contains("BE"));
        assertTrue(exception.getErrorMessageArguments().contains(TRAJ));
    }

    @Test
    void validateMaxPowerFile_doesNotThrowWhenAllOthersAreasAreInFile() throws Exception {
        Path dir = tempDir.resolve("maxpower_others_ok");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, FILE_NAME_MAX_POWER, HORIZON,
                List.of("areas", "FR", "AT", "BE"),
                List.of(List.of(100, 200, 300, 400)));

        assertDoesNotThrow(() ->
                service.validateMaxPowerFile(
                        TrajectoryType.HYDRO_SERIES,
                        filePath,
                        TRAJ,
                        HORIZON,
                        OTHERS_AREA,
                        List.of("FR", "AT", "BE"),
                        List.of("FR", "BE")
                )
        );
    }

    @Test
    void validateMaxPowerFile_doesNotThrowWhenPspColumnsAreValid() throws Exception {
        Path dir = tempDir.resolve("maxpower_psp_ok");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, FILE_NAME_MAX_POWER, HORIZON,
                List.of("areas", "FR_generating", "FR_pumping"),
                List.of(List.of(100, 200)));

        assertDoesNotThrow(() ->
                service.validateMaxPowerFile(
                        TrajectoryType.HYDRO_PSP_SERIES,
                        filePath,
                        TRAJ,
                        HORIZON,
                        AREA_FR,
                        List.of(AREA_FR),
                        List.of()
                )
        );
    }

    @Test
    void validateMaxPowerFile_throwsWhenPspColumnHasStraySpaceBeforeSuffix() throws Exception {
        Path dir = tempDir.resolve("maxpower_psp_space");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, FILE_NAME_MAX_POWER, HORIZON,
                List.of("areas", "FR_ generating"),
                List.of(List.of(100)));

        List<String> studyAreas = List.of(AREA_FR);
        List<String> areasInHydroSeriesModFiles = List.of();

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateMaxPowerFile(
                        TrajectoryType.HYDRO_PSP_SERIES,
                        filePath,
                        TRAJ,
                        HORIZON,
                        AREA_FR,
                        studyAreas,
                        areasInHydroSeriesModFiles
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getErrorMessageArguments().contains("FR_ generating"));
    }

    // -------------------------------------------------------------------------
    // validateEmptyRequiredColumns
    // -------------------------------------------------------------------------

    @Test
    void validateEmptyRequiredColumns_doesNotThrowWhenAllValuesAreValidIntegers() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_ALLOCATION);

        assertDoesNotThrow(() ->
                service.validateEmptyRequiredColumns(context, new String[]{"col1"}, true, false, "100"));
    }

    @Test
    void validateEmptyRequiredColumns_throwsWhenNumericValueIsNull() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_ALLOCATION);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(context, new String[]{"allocation", "extra"}, true, false, (Object) null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Allocation column", exception.getErrorMessageArguments().getFirst());
    }

    @Test
    void validateEmptyRequiredColumns_throwsWhenValueIsNonNumericString() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_PARAMETERS);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(context, new String[]{"col1", "extra"}, true, false, "not_a_number"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Column(s) 2 to 6 and 8", exception.getErrorMessageArguments().getFirst());
    }

    @Test
    void validateEmptyRequiredColumns_throwsWhenBooleanValueIsNull() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_PARAMETERS);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(context, new String[]{"reservoir", "extra"}, false, false, (Object) null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Column(s) 7, 9 and 10", exception.getErrorMessageArguments().getFirst());
    }

    @Test
    void validateEmptyRequiredColumns_throwsWhenBooleanValueIsNonBooleanString() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_PARAMETERS);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(context, new String[]{"reservoir", "extra"}, false, false, "maybe"));

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
    // validateEmptyRequiredColumns – régression off-by-one (length - 1 → length)
    // -------------------------------------------------------------------------

    @Test
    void validateEmptyRequiredColumns_throwsWhenSingleRequiredColumnIsNull() {
        // Avant le fix : loop i < length-1 = 0 → aucune itération → la colonne n'était jamais validée.
        // Après le fix : loop i < length = 1 → la colonne unique est bien validée.
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_ALLOCATION);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(context, new String[]{"allocation"}, true, false, (Object) null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Allocation column", exception.getErrorMessageArguments().getFirst());
    }

    @Test
    void validateEmptyRequiredColumns_throwsWhenLastBooleanColumnIsNull() {
        ResRowProcessingContext context = buildContext(TrajectoryType.HYDRO_PARAMETERS);

        // 3 colonnes, 2 valeurs valides + null en position 3 (index 2)
        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateEmptyRequiredColumns(
                        context,
                        new String[]{"reservoir", "follow.load", "use.water"},
                        false,
                        Boolean.TRUE, Boolean.FALSE, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Column(s) 7, 9 and 10", exception.getErrorMessageArguments().getFirst());
    }

    // -------------------------------------------------------------------------
    // processHydroSeriesFile – régression hasOnlyRorFile multi-areas
    // -------------------------------------------------------------------------

    @Test
    void shouldRequireMaxPowerWhenFirstAreaHasModFilesAndLastAreaHasOnlyRor() throws Exception {
        // → maxpower exigé → exception levée.
        Path base = tempDir.resolve("hydro_multi_area");
        Path traj = base.resolve(TRAJ);

        Path inflowDir = traj.resolve("inflows");
        Path mingenDir = traj.resolve("mingen");
        Path reservoirDir = traj.resolve("reservoir_levels");
        Files.createDirectories(inflowDir);
        Files.createDirectories(mingenDir);
        Files.createDirectories(reservoirDir);

        // FR a des fichiers mod + mingen + reservoir_levels → hasOnlyRorFile = false pour FR
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_ROR);
        CreateExcelTestUtil.createMockCsvFile(mingenDir, FILE_NAME_MINGEN);
        CreateExcelTestUtil.createMockCsvFile(reservoirDir, FILE_NAME_RESERVOIR_LEVELS);
        // BE n'a qu'un fichier ROR → hasOnlyRorFile = true pour BE seul
        CreateExcelTestUtil.createMockCsvFile(inflowDir, "ror_BE_2029-2030.csv");
        // Pas de fichier maxpower → processMaxPowerFile doit lever une exception

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        // FR en premier, BE en second pour que le bug soit reproductible
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(
                new AreaEntity() {{ setName(AREA_FR); }},
                new AreaEntity() {{ setName("BE"); }}
        ));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, OTHERS_AREA, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Missing maxpower file (maxpower_{0}) in Hydro Series trajectory {0}"));
    }

    @Test
    void shouldNotRequireMaxPowerWhenAllAreasHaveOnlyRorFiles() throws Exception {
        // Quand TOUS les areas n'ont que des fichiers ROR, aucun maxpower n'est exigé.
        Path base = tempDir.resolve("hydro_all_ror");
        Path traj = base.resolve(TRAJ);
        Path inflowDir = traj.resolve("inflows");
        // processRequiredSeries appelle toRealPath() sur chaque répertoire série : ils doivent exister.
        Files.createDirectories(inflowDir);
        Files.createDirectories(traj.resolve("mingen"));
        Files.createDirectories(traj.resolve("reservoir_levels"));

        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_ROR);
        CreateExcelTestUtil.createMockCsvFile(inflowDir, "ror_BE_2029-2030.csv");

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(
                new AreaEntity() {{ setName(AREA_FR); }},
                new AreaEntity() {{ setName("BE"); }}
        ));

        TrajectoryEntity result = service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, OTHERS_AREA, false);

        assertNotNull(result);
        // 2 fichiers ROR (FR + BE), aucun maxpower
        assertEquals(2, result.getHydroSeriesEntities().size());
        verify(trajectoryRepository).save(any());
    }

    @Test
    void shouldSaveOnlyRorWhenModPresentButMingenAndReservoirLevelsAbsent_specificArea() throws Exception {
        // Case 1, Scenario 1: inflows has ror + mod, mingen and reservoir_levels dirs are empty.
        // Expected: only ror is saved
        Path base = tempDir.resolve("hydro_no_tp_specific");
        Path traj = base.resolve(TRAJ);
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Files.createDirectories(traj.resolve("mingen"));
        Files.createDirectories(traj.resolve("reservoir_levels"));

        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_ROR);
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        TrajectoryEntity result = service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, AREA_FR, false);

        assertNotNull(result);
        assertEquals(1, result.getHydroSeriesEntities().size());
        assertEquals(FILE_NAME_ROR, result.getHydroSeriesEntities().getFirst().getTsName());
        verify(trajectoryRepository).save(any());
    }

    @Test
    void shouldSaveOnlyRorWhenModPresentButMingenAndReservoirLevelsAbsent_others() throws Exception {
        // Case 1, Scenario 2: same as above, for Others
        Path base = tempDir.resolve("hydro_no_tp_others");
        Path traj = base.resolve(TRAJ);
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Files.createDirectories(traj.resolve("mingen"));
        Files.createDirectories(traj.resolve("reservoir_levels"));

        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_ROR);
        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        TrajectoryEntity result = service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, OTHERS_AREA, false);

        assertNotNull(result);
        assertEquals(1, result.getHydroSeriesEntities().size());
        assertEquals(FILE_NAME_ROR, result.getHydroSeriesEntities().getFirst().getTsName());
        verify(trajectoryRepository).save(any());
    }

    @Test
    void shouldThrowMissingFilesWhenOnlyModPresentAndNoRorOrTechnicalFiles_specificArea() throws Exception {
        // Case 2, Scenario 1: inflows has only mod (no ror), mingen and reservoir_levels are absent
        // Expected: BusinessException "Missing files in hydro series trajectory".
        Path base = tempDir.resolve("hydro_no_tp_mod_only_specific");
        Path traj = base.resolve(TRAJ);
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Files.createDirectories(traj.resolve("mingen"));
        Files.createDirectories(traj.resolve("reservoir_levels"));

        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, AREA_FR, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Missing files in Hydro Series trajectory"));
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void shouldThrowMissingFilesWhenOnlyModPresentAndNoRorOrTechnicalFiles_others() throws Exception {
        // Case 2, Scenario 2: same for Others. mod present but no ror, no mingen, no reservoir_levels.
        Path base = tempDir.resolve("hydro_no_tp_mod_only_others");
        Path traj = base.resolve(TRAJ);
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Files.createDirectories(traj.resolve("mingen"));
        Files.createDirectories(traj.resolve("reservoir_levels"));

        CreateExcelTestUtil.createMockCsvFile(inflowDir, FILE_NAME_MOD);
        CreateExcelTestUtil.createMockCsvFile(inflowDir, "mod_BE_2029-2030.csv");

        when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any())).thenReturn(base);
        when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());
        when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(
                new AreaEntity() {{ setName(AREA_FR); }},
                new AreaEntity() {{ setName("BE"); }}
        ));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TrajectoryType.HYDRO_SERIES, TRAJ, HORIZON, 1, OTHERS_AREA, false));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("Missing files in Hydro Series trajectory"));
        verify(trajectoryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // processRows – guard studyId null sur checkHydroTPTrajectoriesConsistency
    // -------------------------------------------------------------------------

    @Test
    void processTechnicalParametersFile_doesNotCallCoherenceCheckWhenStudyIdIsNull() throws Exception {
        Path dir = tempDir.resolve("coherence_null_study");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, "hydroAllocation_test.xlsx", HORIZON,
                List.of("hydro", "load", "allocation"),
                List.of(List.of("FR", "FR_LOAD", 100)));

        service.processTechnicalParametersFile(
                new TechnicalParametersProcessingContext(filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION, null, null));

        verify(hydroCoherenceCheckService, never())
                .checkHydroTPTrajectoriesConsistency(any(), any(), any(), any(), any(), any());
    }

    @Test
    void processTechnicalParametersFile_callsCoherenceCheckWhenStudyIdIsNotNull() throws Exception {
        Path dir = tempDir.resolve("coherence_with_study");
        Files.createDirectories(dir);
        Path filePath = CreateExcelTestUtil.createExcelFile(dir, "hydroAllocation_test.xlsx", HORIZON,
                List.of("hydro", "load", "allocation"),
                List.of(List.of("FR", "FR_LOAD", 100)));

        service.processTechnicalParametersFile(
                new TechnicalParametersProcessingContext(filePath, TRAJ, HORIZON, AREA_FR, List.of("FR"), TrajectoryType.HYDRO_ALLOCATION, TrajectoryType.HYDRO_SERIES, 1));

        verify(hydroCoherenceCheckService, times(1))
                .checkHydroTPTrajectoriesConsistency(eq(1), any(), eq(AREA_FR), eq(TRAJ), any(), any());
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
