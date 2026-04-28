package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.AreaEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.impl.HydroFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.CreateExcelTestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static reactor.core.publisher.Mono.when;

@ExtendWith(MockitoExtension.class)
class HydroFileProcessorServiceImplTest {

    private static final String AREA_FR = "FR";
    private static final String HORIZON = "2029-2030";
    private static final String TRAJ = "BP_23";
    private static final String DIRECTORY_HYDRO_SERIES = "series";
    private static final String CSV_FILE_NAME = "maxpower_fr_2029-2030.csv";

    @InjectMocks
    private HydroFileProcessorServiceImpl service;

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private TrajectoryRepository trajectoryRepository;
    
    @Mock
    private AreaRepository areaRepository;

    @Mock
    private UserService userService;

    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setup() {
        lenient().when(trajectoryRepository.save(any())) .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldThrowWhenTrajectoryPathIsOutsideDirectory() throws IOException {
        // Répertoire de base autorisé
        Path base = tempDir.resolve("hydro");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj);

        // Création d'un répertoire "outside" réel, mais hors du répertoire autorisé
        Path outside = tempDir.resolveSibling("outside");
        Files.createDirectories(outside);
        
        Mockito.when(trajectoryService.normalizeAndValidateDirectory(any(), any(), any()))
                .thenReturn(outside);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.processHydroSeriesFile(TRAJ, HORIZON, 1, AREA_FR, false)
        );

        assertTrue(exception.getMessage().contains("Invalid trajectory path"));
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
        CreateExcelTestUtil.createExcelFile(traj, "maxpower_fr_2029-2030.xlsx", "2029-2030", List.of("areas", "AT","BE", "FR"),
                List.of(
                        List.of(2345, 2345, 2345, 2345)
                ));
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = traj.resolve("mingen");
        Files.createDirectories(mingenDir);
        Path reservoirLevels = traj.resolve("reservoir_levels");
        Files.createDirectories(reservoirLevels);
        
        CreateExcelTestUtil.createMockCsvFile(inflowDir,"mod_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(inflowDir,"ror_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(mingenDir,"mingen_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(reservoirLevels,"reservoir_levels_FR_2029-2030.csv");
        
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
        CreateExcelTestUtil.createExcelFile(traj, "maxpower_fr_2029-2030.xlsx", "2029-2030", List.of("areas", "AT","BE", "FR"),
                List.of(
                        List.of(2345, 2345, 2345, 2345)
                ));
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = traj.resolve("mingen");
        Files.createDirectories(mingenDir);
        Path reservoirLevels = traj.resolve("reservoir_levels");
        Files.createDirectories(reservoirLevels);

        CreateExcelTestUtil.createMockCsvFile(inflowDir,"mod_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(inflowDir,"ror_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(mingenDir,"mingen_FR_2029-2030.csv");

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
        CreateExcelTestUtil.createExcelFile(traj, "maxpower_fr_2029-2030.xlsx", "2029-2030", List.of("areas", "AT","BE", "YU"),
                List.of(
                        List.of(2345, 2345, 2345, 2345)
                ));
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = traj.resolve("mingen");
        Files.createDirectories(mingenDir);
        Path reservoirLevels = traj.resolve("reservoir_levels");
        Files.createDirectories(reservoirLevels);

        CreateExcelTestUtil.createMockCsvFile(inflowDir,"mod_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(inflowDir,"ror_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(mingenDir,"mingen_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(reservoirLevels,"reservoir_levels_FR_2029-2030.csv");

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
    void shouldThrowWhenMaxPowerHasNoAreasOfTrajectoryArea() throws Exception {
        Path base = tempDir.resolve("hydro");
        Path traj = base.resolve(TRAJ);
        Files.createDirectories(traj);
        CreateExcelTestUtil.createExcelFile(traj, "maxpower_fr_2029-2030.xlsx", "2029-2030", List.of("areas", "AT","BE", "YU"),
                List.of(
                        List.of(2345, 2345, 2345, 2345)
                ));
        Path inflowDir = traj.resolve("inflows");
        Files.createDirectories(inflowDir);
        Path mingenDir = traj.resolve("mingen");
        Files.createDirectories(mingenDir);
        Path reservoirLevels = traj.resolve("reservoir_levels");
        Files.createDirectories(reservoirLevels);

        CreateExcelTestUtil.createMockCsvFile(inflowDir,"mod_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(inflowDir,"ror_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(mingenDir,"mingen_FR_2029-2030.csv");
        CreateExcelTestUtil.createMockCsvFile(reservoirLevels,"reservoir_levels_FR_2029-2030.csv");

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
        assertTrue(exception.getMessage().contains("None of the areas of trajectory AREA are present in {0} trajectory {1}"));
    }
}
    
