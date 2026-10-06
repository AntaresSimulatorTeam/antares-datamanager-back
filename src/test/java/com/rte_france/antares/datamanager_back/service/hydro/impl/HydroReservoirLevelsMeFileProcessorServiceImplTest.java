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
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HydroReservoirLevelsMeFileProcessorServiceImplTest {

    @Mock(lenient = true)
    private TrajectoryRepository trajectoryRepository;

    @Mock(lenient = true)
    private HydroCapacityMeRepository hydroCapacityMeRepository;

    @Mock(lenient = true)
    private UserService userService;

    @Mock(lenient = true)
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @InjectMocks
    private HydroReservoirLevelsMeFileProcessorServiceImpl hydroReservoirLevelsMeFileProcessorService;

    @TempDir
    Path tempDir;

    private String testTrajectoryName;
    private String testHorizon;
    private Path trajectoryPath;
    private Integer studyId = 1;

    @BeforeEach
    void setUp() throws IOException {
        testTrajectoryName = "test_hydro_res";
        testHorizon = "2020-2021";

        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("USER123").build());

        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(tempDir.toString());

        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");

        when(antaresDataManagerProperties.getHydroReservoirLevelsMeDirectory())
                .thenReturn("ME/hydro_ME/reservoir_levels");

        setupTestDirectory();
    }

    private void setupTestDirectory() throws IOException {
        Path reservoirLevelsPath = tempDir.resolve("trajectories/ME/hydro_ME/reservoir_levels");
        trajectoryPath = reservoirLevelsPath.resolve(testTrajectoryName);
        Files.createDirectories(trajectoryPath);

        // Extract the second part of horizon (e.g., "2021" from "2020-2021")
        String secondPartOfHorizon = testHorizon.split("-")[1];
        
        createExcelFileWithSheet(trajectoryPath.resolve("node1_reservoir_levels.xlsx"), secondPartOfHorizon);
        createExcelFileWithSheet(trajectoryPath.resolve("node2_reservoir_levels.xlsx"), secondPartOfHorizon);
    }

    private void createExcelFile(Path filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sheet1");
            try (OutputStream out = Files.newOutputStream(filePath)) {
                workbook.write(out);
            }
        }
    }

    private void createExcelFileWithSheet(Path filePath, String sheetName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet(sheetName);
            try (OutputStream out = Files.newOutputStream(filePath)) {
                workbook.write(out);
            }
        }
    }

    @Test
    @DisplayName("Should successfully process hydro reservoir levels ME directory")
    void testProcessHydroReservoirLevelsMeFileSuccess() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        TrajectoryEntity savedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name())
                .horizon(testHorizon)
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(savedTrajectory);

        TrajectoryEntity result = hydroReservoirLevelsMeFileProcessorService
                .processHydroReservoirLevelsMeFile(testTrajectoryName, testHorizon, studyId);

        assertNotNull(result);
        assertEquals(testTrajectoryName, result.getFileName());
        assertEquals(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name(), result.getType());
        assertEquals(1, result.getVersion());
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
                .type(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name())
                .horizon(testHorizon)
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(savedTrajectory);

        TrajectoryEntity result = hydroReservoirLevelsMeFileProcessorService
                .processHydroReservoirLevelsMeFile(testTrajectoryName, testHorizon, studyId);

        assertNotNull(result);
        assertEquals(testTrajectoryName, result.getFileName());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should successfully process when at least one node exists in HYDRO_CAPACITY_ME")
    void testProcessWithSomeValidNodes() throws IOException {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "non_existing_node"));

        TrajectoryEntity savedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name())
                .horizon(testHorizon)
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(savedTrajectory);

        TrajectoryEntity result = hydroReservoirLevelsMeFileProcessorService
                .processHydroReservoirLevelsMeFile(testTrajectoryName, testHorizon, studyId);

        assertNotNull(result);
        assertEquals(testTrajectoryName, result.getFileName());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should throw BusinessException when no file matches valid nodes from HYDRO_CAPACITY_ME")
    void testProcessWithNoValidNodes_throwsBusinessException() throws IOException {
        String secondPartOfHorizon = testHorizon.split("-")[1];
        
        // Create files with node names that don't match valid nodes
        String otherDir = "other_trajectory";
        Path otherTrajPath = tempDir.resolve("trajectories/ME/hydro_ME/reservoir_levels").resolve(otherDir);
        Files.createDirectories(otherTrajPath);
        createExcelFileWithSheet(otherTrajPath.resolve("other_node1_reservoir_levels.xlsx"), secondPartOfHorizon);
        createExcelFileWithSheet(otherTrajPath.resolve("other_node2_reservoir_levels.xlsx"), secondPartOfHorizon);

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile(otherDir, testHorizon, studyId));

        assertEquals("No file related to the nodes of the HYDRO_ME_CAPACITY trajectory in HYDRO_ME Reservoir Levels trajectory {0}", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertThat(exception.getErrorMessageArguments()).containsExactly(otherDir);
    }

    @Test
    @DisplayName("Should throw BusinessException when user NNI cannot be determined")
    void testProcessWhenUserNniNull_throwsBusinessException() {
        when(userService.getCurrentUserDetails()).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile(testTrajectoryName, testHorizon, studyId));

        assertEquals("User NNI could not be determined", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should throw BusinessException when Antares path configuration is incomplete")
    void testProcessWhenConfigIncomplete_throwsBusinessException() {
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile(testTrajectoryName, testHorizon, studyId));

        assertEquals("Antares path configuration is incomplete", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    @DisplayName("Should throw IOException when trajectory path traverses outside base directory")
    void testProcessWhenPathTraversal_throwsIOException() {
        assertThrows(IOException.class, () ->
                hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile("../../../outside", testHorizon, studyId));
    }

    @Test
    @DisplayName("Should throw BusinessException when trajectory path is not a directory")
    void testProcessWhenTrajectoryNotADirectory_throwsBusinessException() throws IOException {
        String fileTrajectory = "file_trajectory";
        Path reservoirLevelsPath = tempDir.resolve("trajectories/ME/hydro_ME/reservoir_levels");
        Files.createFile(reservoirLevelsPath.resolve(fileTrajectory));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile(fileTrajectory, testHorizon, studyId));

        assertEquals("Trajectory must be a directory: {0}", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertThat(exception.getErrorMessageArguments()).containsExactly(fileTrajectory);
    }

    @Test
    @DisplayName("Should throw BusinessException when trajectory directory has no regular files")
    void testProcessWhenTrajectoryDirectoryEmpty_throwsBusinessException() throws IOException {
        String emptyTrajectory = "empty_trajectory";
        Path reservoirLevelsPath = tempDir.resolve("trajectories/ME/hydro_ME/reservoir_levels");
        Files.createDirectories(reservoirLevelsPath.resolve(emptyTrajectory));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile(emptyTrajectory, testHorizon, studyId));

        assertEquals("No Excel files found in the directory: {0}", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertThat(exception.getErrorMessageArguments()).containsExactly(emptyTrajectory);
    }

    @Test
    @DisplayName("Should throw BusinessException when existing trajectory has same content")
    void testProcessWithExistingTrajectoryWithSameContent() {
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Arrays.asList("node1", "node2"));

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(testTrajectoryName)
                .type(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name())
                .horizon(testHorizon)
                .version(1)
                .checksum("same_checksum")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name()))
                .thenReturn(Optional.of(existingTrajectory));

        try (var utils = mockStatic(Utils.class)) {
            utils.when(() -> Utils.calculateDirectorySize(any()))
                    .thenReturn(100L);
            utils.when(() -> Utils.isSameFileWithSameContent(any(), any()))
                    .thenReturn(true);

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile(testTrajectoryName, testHorizon, studyId));

            assertEquals("File already processed with same content: {0}", exception.getMessage());
            assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
            assertThat(exception.getErrorMessageArguments()).containsExactly(testTrajectoryName);
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
                .type(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name())
                .horizon(testHorizon)
                .version(2)
                .checksum("old_checksum")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                testTrajectoryName, testHorizon, TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name()))
                .thenReturn(Optional.of(existingTrajectory));

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        try (var utils = mockStatic(Utils.class)) {
            utils.when(() -> Utils.calculateDirectorySize(any()))
                    .thenReturn(100L);
            utils.when(() -> Utils.computeChecksumByType(any(), any(), any(), any()))
                    .thenReturn("new_checksum");
            utils.when(() -> Utils.isSameFileWithSameContent(any(), any()))
                    .thenReturn(false);

            TrajectoryEntity result = hydroReservoirLevelsMeFileProcessorService
                    .processHydroReservoirLevelsMeFile(testTrajectoryName, testHorizon, studyId);

            assertNotNull(result);
            assertEquals(3, result.getVersion());
            assertEquals(testTrajectoryName, result.getFileName());
            assertEquals("USER123", result.getCreatedBy());
            assertEquals(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name(), result.getType());
            verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        }
    }
}
