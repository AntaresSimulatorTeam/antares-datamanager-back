package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.impl.HydroFileProcessorServiceImpl;
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

        // Mock study areas
        Mockito.when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        TrajectoryEntity result = service.processHydroSeriesFile(TRAJ, HORIZON, 1, AREA_FR, false);

        assertNotNull(result);
        assertEquals(5, result.getHydroSeriesEntities().size());
    }

    @Test
    void shouldThrowWhenRequiredSeriesFolderIsEmpty() throws Exception {
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

        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any()))
                .thenReturn(base);
        Mockito.when(trajectoryService.buildDirectoryTrajectory(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TrajectoryEntity());

        // Mock study areas
        Mockito.when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TRAJ, HORIZON, 1, AREA_FR, false)
        );
        assertTrue(exception.getMessage().contains("No files found for HYDRO series trajectory in reservoir_levels"));
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

        // Mock study areas
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
        // Given
        Path baseDirectory = Path.of("/tmp/hydro/series").normalize();

        when(trajectoryService.normalizeAndValidateDirectory(
                eq(TrajectoryType.HYDRO_SERIES),
                eq("FR"),
                isNull()
        )).thenReturn(baseDirectory);

        String maliciousTrajectoryToUse = "../outside";

        // When / Then
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
        // Given
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
        Path inflowsDirectory = trajectoryPath.resolve("inflows");

        Files.createDirectories(inflowsDirectory);

        // Nom invalide : il manque le horizon au format attendu avant .csv
        Files.createFile(inflowsDirectory.resolve("ror_FR.csv"));

        when(trajectoryService.normalizeAndValidateDirectory(
                eq(TrajectoryType.HYDRO_SERIES),
                eq(area),
                isNull()
        )).thenReturn(baseDirectory);

        when(trajectoryService.buildDirectoryTrajectory(
                eq(TrajectoryType.HYDRO_SERIES.name()),
                eq(trajectoryToUse),
                eq(trajectoryPath),
                eq(horizon),
                eq(area),
                isNull()
        )).thenReturn(TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .type(TrajectoryType.HYDRO_SERIES.name())
                .horizon(horizon)
                .area(area)
                .build());

        // Mock study areas
        Mockito.when(areaRepository.findAllByStudyId(1)).thenReturn(List.of(new AreaEntity() {{
            setName(AREA_FR);
        }}, new AreaEntity() {{
            setName("AT");
        }}, new AreaEntity() {{
            setName("BE");
        }}, new AreaEntity() {{
            setName("FR");
        }}));

        // When / Then
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
        assertEquals("No files found for HYDRO series trajectory in inflows", exception.getMessage());

        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void validateMaxPowerFile_throwsBusinessException_whenFilePathIsNull() {
        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.validateMaxPowerFile(
                        null,
                        TRAJ,
                        HORIZON,
                        AREA_FR,
                        List.of(AREA_FR),
                        TrajectoryType.HYDRO_SERIES
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Not foundnull", exception.getMessage());
    }
}
