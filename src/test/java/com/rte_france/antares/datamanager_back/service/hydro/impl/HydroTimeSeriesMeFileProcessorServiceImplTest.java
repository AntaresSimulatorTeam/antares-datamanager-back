package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.HydroCapacityMeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class HydroTimeSeriesMeFileProcessorServiceImplTest {

    @Mock(lenient = true)
    private TrajectoryRepository trajectoryRepository;

    @Mock(lenient = true)
    private HydroCapacityMeRepository hydroCapacityMeRepository;

    @Mock(lenient = true)
    private UserService userService;

    @Mock(lenient = true)
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @InjectMocks
    private HydroTimeSeriesMeFileProcessorServiceImpl hydroTimeSeriesMeFileProcessorService;

    @TempDir
    Path tempDir;

    private String testTrajectoryName;
    private String testHorizon;
    private Path trajectoryPath;
    private Integer studyId = 1;

    @BeforeEach
    void setUp() throws IOException {
        testTrajectoryName = "test_hydro_ts";
        testHorizon = "2020-2021";
        
        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());
        
        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(tempDir.toString());
        
        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");
        
        when(antaresDataManagerProperties.getHydroTimeSeriesMeDirectory())
                .thenReturn("ME/hydro_ME/timeseries");
        
        setupTestDirectory();
    }

    private void setupTestDirectory() throws IOException {
        Path timeseriesPath = tempDir.resolve("trajectories/ME/hydro_ME/timeseries");
        trajectoryPath = timeseriesPath.resolve(testTrajectoryName);
        Files.createDirectories(trajectoryPath);
        
        // Extract the second part of horizon (e.g., "2021" from "2020-2021")
        String secondPartOfHorizon = testHorizon.split("-")[1];
        
        // Create node directories with required files containing the horizon sheet
        Path node1Dir = trajectoryPath.resolve("node1");
        Files.createDirectories(node1Dir);
        createExcelFileWithSheet(node1Dir.resolve("mod.xlsx"), secondPartOfHorizon);
        createExcelFileWithSheet(node1Dir.resolve("ror.xlsx"), secondPartOfHorizon);
        
        Path node2Dir = trajectoryPath.resolve("node2");
        Files.createDirectories(node2Dir);
        createExcelFileWithSheet(node2Dir.resolve("mod.xlsx"), secondPartOfHorizon);
        createExcelFileWithSheet(node2Dir.resolve("ror.xlsx"), secondPartOfHorizon);
    }

    @Test
    @DisplayName("Should successfully process hydro time series ME directory")
    void testProcessHydroTimeSeriesMeDirectorySuccess() throws IOException {
        TrajectoryEntity savedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_TIME_SERIES_ME.name())
                .horizon(testHorizon)
                .build();

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_TIME_SERIES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(savedTrajectory);

        TrajectoryEntity result = hydroTimeSeriesMeFileProcessorService
                .processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId);

        assertNotNull(result);
        assertEquals(testTrajectoryName, result.getFileName());
        assertEquals(TrajectoryType.HYDRO_TIME_SERIES_ME.name(), result.getType());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should successfully process when no HYDRO_CAPACITY_ME exists (no validation needed)")
    void testProcessWithNoHydroCapacityMeTrajectory() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of());

        TrajectoryEntity savedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_TIME_SERIES_ME.name())
                .horizon(testHorizon)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_TIME_SERIES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(savedTrajectory);

        TrajectoryEntity result = hydroTimeSeriesMeFileProcessorService
                .processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId);

        assertNotNull(result);
        assertEquals(testTrajectoryName, result.getFileName());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should successfully process when at least one node exists in HYDRO_CAPACITY_ME (other nodes ignored)")
    void testProcessWithSomeValidNodes() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        // Add a node3 that doesn't exist in HYDRO_CAPACITY_ME
        String secondPartOfHorizon = testHorizon.split("-")[1];
        Path node3Dir = trajectoryPath.resolve("node3");
        Files.createDirectories(node3Dir);
        createExcelFileWithSheet(node3Dir.resolve("mod.xlsx"), secondPartOfHorizon);
        createExcelFileWithSheet(node3Dir.resolve("ror.xlsx"), secondPartOfHorizon);

        TrajectoryEntity savedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_TIME_SERIES_ME.name())
                .horizon(testHorizon)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_TIME_SERIES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(savedTrajectory);

        TrajectoryEntity result = hydroTimeSeriesMeFileProcessorService
                .processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId);

        assertNotNull(result);
        assertEquals(testTrajectoryName, result.getFileName());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when no nodes exist in HYDRO_CAPACITY_ME")
    void testProcessWithNoValidNodes() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("valid_node1", "valid_node2"));

        // All node directories don't exist in HYDRO_CAPACITY_ME
        String secondPartOfHorizon = testHorizon.split("-")[1];
        Path invalidNode1 = trajectoryPath.resolve("invalid_node1");
        Files.createDirectories(invalidNode1);
        createExcelFileWithSheet(invalidNode1.resolve("mod.xlsx"), secondPartOfHorizon);
        createExcelFileWithSheet(invalidNode1.resolve("ror.xlsx"), secondPartOfHorizon);

        Path invalidNode2 = trajectoryPath.resolve("invalid_node2");
        Files.createDirectories(invalidNode2);
        createExcelFileWithSheet(invalidNode2.resolve("mod.xlsx"), secondPartOfHorizon);
        createExcelFileWithSheet(invalidNode2.resolve("ror.xlsx"), secondPartOfHorizon);

        // Delete the original node1 and node2
        Files.walk(trajectoryPath.resolve("node1"))
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        Files.walk(trajectoryPath.resolve("node2"))
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        BusinessException exception = assertThrows(BusinessException.class, () ->
                hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId));

        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when node directory missing mod.xlsx")
    void testProcessWithMissingModFile() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2", "node_incomplete"));

        // Create node_incomplete with only ror.xlsx (missing mod.xlsx)
        String secondPartOfHorizon = testHorizon.split("-")[1];
        Path incompleteNode = trajectoryPath.resolve("node_incomplete");
        Files.createDirectories(incompleteNode);
        createExcelFileWithSheet(incompleteNode.resolve("ror.xlsx"), secondPartOfHorizon);

        assertThrows(BusinessException.class, () ->
                hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId));
    }

    @Test
    @DisplayName("Should throw exception when node directory missing ror.xlsx")
    void testProcessWithMissingRorFile() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2", "node_incomplete2"));

        // Create node_incomplete2 with only mod.xlsx (missing ror.xlsx)
        String secondPartOfHorizon = testHorizon.split("-")[1];
        Path incompleteNode2 = trajectoryPath.resolve("node_incomplete2");
        Files.createDirectories(incompleteNode2);
        createExcelFileWithSheet(incompleteNode2.resolve("mod.xlsx"), secondPartOfHorizon);

        assertThrows(BusinessException.class, () ->
                hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId));
    }

    @Test
    @DisplayName("Should throw exception when no node directories found")
    void testProcessWithNoNodeDirectories() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        // Clean up all node directories
        Path node1 = trajectoryPath.resolve("node1");
        Path node2 = trajectoryPath.resolve("node2");
        Files.walk(node1)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        Files.walk(node2)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        assertThrows(BusinessException.class, () ->
                hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId));
    }

    @Test
    @DisplayName("Should throw exception when existing trajectory has same content")
    void testProcessWithExistingTrajectoryWithSameContent() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_TIME_SERIES_ME.name())
                .horizon(testHorizon)
                .version(1)
                .checksum("same_checksum")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_TIME_SERIES_ME.name()))
                .thenReturn(Optional.of(existingTrajectory));

        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());

        try (var utils = mockStatic(Utils.class)) {
            utils.when(() -> Utils.isSameFileWithSameContent(any(), any()))
                    .thenReturn(true);

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId));

            assertNotNull(exception.getMessage());
            assertTrue(exception.getMessage().contains("Directory already processed with same content"));
        }
    }

    @Test
    @DisplayName("Should create new version when existing trajectory has different content")
    void testProcessWithExistingTrajectoryWithDifferentContent() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_TIME_SERIES_ME.name())
                .horizon(testHorizon)
                .version(2)
                .checksum("old_checksum")
                .build();

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(2)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_TIME_SERIES_ME.name())
                .horizon(testHorizon)
                .version(3)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_TIME_SERIES_ME.name()))
                .thenReturn(Optional.of(existingTrajectory));

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(newTrajectory);

        try (var utils = mockStatic(Utils.class)) {
            utils.when(() -> Utils.isSameFileWithSameContent(any(), any()))
                    .thenReturn(false);

            TrajectoryEntity result = hydroTimeSeriesMeFileProcessorService
                    .processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId);

            assertNotNull(result);
            assertEquals(3, result.getVersion());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }
    }

    @Test
    @DisplayName("Should throw exception when mod.xlsx is missing required sheet")
    void testProcessWithMissingRequiredSheetInModFile() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        // Create node with mod.xlsx missing the required sheet
        String secondPartOfHorizon = testHorizon.split("-")[1];
        Path node1Dir = trajectoryPath.resolve("node1");
        createExcelFileWithSheet(node1Dir.resolve("mod.xlsx"), "2020"); // Wrong sheet name
        createExcelFileWithSheet(node1Dir.resolve("ror.xlsx"), secondPartOfHorizon); // Correct sheet

        BusinessException exception = assertThrows(BusinessException.class, () ->
                hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId));

        assertTrue(exception.getMessage().contains("does not contain required sheet"));
        assertTrue(exception.getMessage().contains("2021"));
        assertTrue(exception.getMessage().contains("mod.xlsx"));
    }

    @Test
    @DisplayName("Should throw exception when ror.xlsx is missing required sheet")
    void testProcessWithMissingRequiredSheetInRorFile() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        // Create node with ror.xlsx missing the required sheet
        String secondPartOfHorizon = testHorizon.split("-")[1];
        Path node1Dir = trajectoryPath.resolve("node1");
        createExcelFileWithSheet(node1Dir.resolve("mod.xlsx"), secondPartOfHorizon); // Correct sheet
        createExcelFileWithSheet(node1Dir.resolve("ror.xlsx"), "2020"); // Wrong sheet name

        BusinessException exception = assertThrows(BusinessException.class, () ->
                hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId));

        assertTrue(exception.getMessage().contains("does not contain required sheet"));
        assertTrue(exception.getMessage().contains("2021"));
        assertTrue(exception.getMessage().contains("ror.xlsx"));
    }

    @Test
    @DisplayName("Should successfully process when all sheets are present with correct names")
    void testProcessWithAllRequiredSheets() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        TrajectoryEntity savedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_TIME_SERIES_ME.name())
                .horizon(testHorizon)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_TIME_SERIES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(savedTrajectory);

        TrajectoryEntity result = hydroTimeSeriesMeFileProcessorService
                .processHydroTimeSeriesMeDirectory(testTrajectoryName, testHorizon, studyId);

        assertNotNull(result);
        assertEquals(testTrajectoryName, result.getFileName());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    private void createExcelFileWithSheet(Path filePath, String sheetName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet(sheetName);
            try (OutputStream out = Files.newOutputStream(filePath)) {
                workbook.write(out);
            }
        }
    }
}

