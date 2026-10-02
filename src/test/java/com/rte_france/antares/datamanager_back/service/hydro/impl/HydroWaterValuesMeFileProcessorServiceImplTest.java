package com.rte_france.antares.datamanager_back.service.hydro.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.HydroCapacityMeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HydroWaterValuesMeFileProcessorService Tests")
class HydroWaterValuesMeFileProcessorServiceImplTest {

    @Mock(lenient = true)
    private TrajectoryRepository trajectoryRepository;

    @Mock(lenient = true)
    private UserService userService;

    @Mock(lenient = true)
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock(lenient = true)
    private HydroCapacityMeRepository hydroCapacityMeRepository;

    @Mock(lenient = true)
    private TrajectoryServiceImpl trajectoryService;

    @InjectMocks
    private HydroMeFileProcessorServiceImpl hydroMeFileProcessorService;

    @TempDir
    Path tempDir;

    private String trajectoryName;
    private String horizon;
    private Integer studyId;
    private Path waterValuesDir;

    @BeforeEach
    void setUp() throws IOException {
        trajectoryName = "water_values_trajectory";
        horizon = "2029-2030";
        studyId = 1;

        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("TEST_USER").build());

        when(antaresDataManagerProperties.getNasDirectory())
                .thenReturn(tempDir.toString());

        when(antaresDataManagerProperties.getTrajectoryFilePath())
                .thenReturn("trajectories");

        when(antaresDataManagerProperties.getHydroWaterValuesMeDirectory())
                .thenReturn("ME/hydro_ME/water_values");

        // Mock buildTrajectoryPath
        when(trajectoryService.buildTrajectoryPath(anyString(), eq(TrajectoryType.HYDRO_WATER_VALUES_ME)))
                .thenAnswer(invocation -> {
                    String trajectoryName = invocation.getArgument(0);
                    return tempDir.resolve("trajectories/ME/hydro_ME/water_values").resolve(trajectoryName);
                });

        waterValuesDir = tempDir.resolve("trajectories/ME/hydro_ME/water_values/" + trajectoryName);
        Files.createDirectories(waterValuesDir);
    }

    @Test
    @DisplayName("Should successfully process HYDRO_WATER_VALUES_ME with valid directory and files")
    void testProcessHydroWaterValuesMeDirectory_Success() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_node1.xlsx"));
        
        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1", "node2"));

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                trajectoryName, horizon, studyId);

        // Assert
        assertNotNull(result);
        assertEquals(trajectoryName, result.getFileName());
        assertEquals(horizon, result.getHorizon());
        assertEquals(TrajectoryType.HYDRO_WATER_VALUES_ME.name(), result.getType());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when trajectory is not a directory")
    void testProcessHydroWaterValuesMeDirectory_NotADirectory() throws IOException {
        // Arrange
        Path filePath = waterValuesDir.getParent().resolve("file.txt");
        Files.createFile(filePath);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                        filePath.getFileName().toString(), horizon, studyId));

        assertTrue(exception.getMessage().contains("Trajectory must be a directory"));
    }

    @Test
    @DisplayName("Should throw exception when no Excel files exist in directory")
    void testProcessHydroWaterValuesMeDirectory_NoExcelFiles() throws IOException {
        // Arrange
        // Directory is empty, no .xlsx files
        
        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                        trajectoryName, horizon, studyId));

        // Check for any of the expected error messages
        String message = exception.getMessage();
        assertNotNull(message, "Exception message should not be null");
        assertTrue(!message.isEmpty(), "Exception message should not be empty");
    }

    @Test
    @DisplayName("Should throw exception when no files match valid nodes from HYDRO_CAPACITY_ME")
    void testProcessHydroWaterValuesMeDirectory_NoMatchingNodes() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_unknown_node.xlsx"));

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1", "node2", "node3"));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                        trajectoryName, horizon, studyId));

        assertTrue(exception.getMessage().contains("No file related to the nodes of the HYDRO_ME_CAPACITY trajectory"));
    }

    @Test
    @DisplayName("Should handle case-insensitive node matching")
    void testProcessHydroWaterValuesMeDirectory_CaseInsensitiveMatching() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_NODE1.xlsx"));

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1", "node2"));

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                trajectoryName, horizon, studyId);

        // Assert
        assertNotNull(result);
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should handle multiple Excel files with valid nodes")
    void testProcessHydroWaterValuesMeDirectory_MultipleValidFiles() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_node1.xlsx"));
        createExcelFile(waterValuesDir.resolve("waterValues_node2.xlsx"));
        createExcelFile(waterValuesDir.resolve("waterValues_node3.xlsx"));

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1", "node2"));

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                trajectoryName, horizon, studyId);

        // Assert
        assertNotNull(result);
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should handle case when no HYDRO_CAPACITY_ME nodes exist (empty study)")
    void testProcessHydroWaterValuesMeDirectory_NoCapacityMeNodes() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_node1.xlsx"));

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(Collections.emptyList());

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                trajectoryName, horizon, studyId);

        // Assert
        assertNotNull(result);
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should increment version when trajectory already exists")
    void testProcessHydroWaterValuesMeDirectory_VersionIncrement() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_node1.xlsx"));

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1"));

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(1)
                .checksum("old_checksum")
                .build();

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(2)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(2)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .thenReturn(Optional.of(existingTrajectory));

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(newTrajectory);

        // Act
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                trajectoryName, horizon, studyId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getVersion());
        verify(trajectoryRepository, times(1)).save(argThat(entity ->
                entity.getVersion() == 2 && entity.getFileName().equals(trajectoryName)
        ));
    }

    @Test
    @DisplayName("Should save new version when trajectory with different content already exists")
    void testProcessHydroWaterValuesMeDirectory_DifferentContent_IncrementVersion() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_node1.xlsx"));

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1"));

        // Simulate an existing trajectory with version 1
        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(1)
                .checksum("different_checksum") // Different from new file
                .build();

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(2)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(2)
                .checksum("new_checksum")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .thenReturn(Optional.of(existingTrajectory));

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(newTrajectory);

        // Act
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                trajectoryName, horizon, studyId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getVersion());
        assertEquals("new_checksum", result.getChecksum());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when user details cannot be determined")
    void testProcessHydroWaterValuesMeDirectory_NoUserDetails() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_node1.xlsx"));

        when(userService.getCurrentUserDetails()).thenReturn(null);

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1"));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                        trajectoryName, horizon, studyId));

        assertTrue(exception.getMessage().contains("User NNI could not be determined"));
    }

    @Test
    @DisplayName("Should correctly set trajectory metadata")
    void testProcessHydroWaterValuesMeDirectory_MetadataCorrect() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_node1.xlsx"));

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1"));

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .thenReturn(Optional.empty());

        TrajectoryEntity capturedEntity = null;

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenAnswer(invocation -> {
                    TrajectoryEntity arg = invocation.getArgument(0);
                    return TrajectoryEntity.builder()
                            .id(1)
                            .fileName(arg.getFileName())
                            .horizon(arg.getHorizon())
                            .type(arg.getType())
                            .version(arg.getVersion())
                            .createdBy(arg.getCreatedBy())
                            .build();
                });

        // Act
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                trajectoryName, horizon, studyId);

        // Assert
        assertNotNull(result);
        assertEquals(trajectoryName, result.getFileName());
        assertEquals(horizon, result.getHorizon());
        assertEquals(TrajectoryType.HYDRO_WATER_VALUES_ME.name(), result.getType());
        assertEquals(1, result.getVersion());
        assertEquals("TEST_USER", result.getCreatedBy());
    }

    @Test
    @DisplayName("Should reject partial node matches in filenames")
    void testProcessHydroWaterValuesMeDirectory_PartialNodeMatches() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_v_me_h2_long_roe.xlsx"));

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("v_me_h2", "other_node"));

        assertThrows(BusinessException.class, () ->
                hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(trajectoryName, horizon, studyId));
        verify(trajectoryRepository, never()).save(any(TrajectoryEntity.class));
    }

    @Test
    @DisplayName("Should ignore non-Excel files in directory")
    void testProcessHydroWaterValuesMeDirectory_IgnoreNonExcelFiles() throws IOException {
        // Arrange
        createExcelFile(waterValuesDir.resolve("waterValues_node1.xlsx"));
        Files.createFile(waterValuesDir.resolve("readme.txt"));
        Files.createFile(waterValuesDir.resolve("data.csv"));

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1"));

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                trajectoryName, horizon, studyId);

        // Assert
        assertNotNull(result);
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    private void createExcelFile(Path filePath) throws IOException {
        createExcelFileWithSheet(filePath, "2030"); // Default sheet name based on horizon "2029-2030"
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
    @DisplayName("Should throw exception when Excel file is missing required sheet")
    void testProcessHydroWaterValuesMeDirectory_MissingRequiredSheet() throws IOException {
        // Arrange - Create Excel file without the required sheet "2030"
        createExcelFileWithSheet(waterValuesDir.resolve("waterValues_node1.xlsx"), "2031");

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1"));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                        trajectoryName, horizon, studyId));

        assertTrue(exception.getMessage().contains("does not contain required sheet"), 
                "Exception message should contain 'does not contain required sheet'. Got: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("2030"), 
                "Exception message should contain '2030'. Got: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should successfully process when all Excel files have required sheet")
    void testProcessHydroWaterValuesMeDirectory_AllFilesHaveRequiredSheet() throws IOException {
        // Arrange - Create Excel files with the required sheet "2030"
        createExcelFileWithSheet(waterValuesDir.resolve("waterValues_node1.xlsx"), "2030");
        createExcelFileWithSheet(waterValuesDir.resolve("waterValues_node2.xlsx"), "2030");

        when(hydroCapacityMeRepository.findDistinctNodesByStudyId(studyId))
                .thenReturn(List.of("node1", "node2"));

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                trajectoryName, horizon, TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class)))
                .thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                trajectoryName, horizon, studyId);

        // Assert
        assertNotNull(result);
        assertEquals(trajectoryName, result.getFileName());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }
}
