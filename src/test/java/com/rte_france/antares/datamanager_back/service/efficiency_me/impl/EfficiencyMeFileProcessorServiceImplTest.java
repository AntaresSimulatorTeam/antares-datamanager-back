package com.rte_france.antares.datamanager_back.service.efficiency_me.impl;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.EfficiencyMeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.EfficiencyMeEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EfficiencyMeFileProcessorServiceImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private EfficiencyMeRepository efficiencyMeRepository;

    @InjectMocks
    private EfficiencyMeFileProcessorServiceImpl service;

    @TempDir
    Path tempDir;

    private Path nasDir;
    private Path trajectoryFilePath;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        
        nasDir = tempDir.resolve("nas");
        trajectoryFilePath = nasDir.resolve("trajectories").resolve("efficiency_me");
        Files.createDirectories(trajectoryFilePath);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(nasDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getEfficiencyMeDirectory()).thenReturn("efficiency_me");
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("USER123").build());
    }

    @Test
    void processEfficiencyMeFile_success() throws IOException {
        // Given
        String trajectoryName = "test_trajectory";
        String horizon = "2023-2024";
        Path trajectoryFile = createTestExcelFile(trajectoryName, horizon);

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .type(TrajectoryType.EFFICIENCY_ME.name())
                .version(1)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenReturn(newTrajectory);

        // When
        TrajectoryEntity result = service.processEfficiencyMeFile(trajectoryName, horizon, 1);

        // Then
        assertNotNull(result);
        assertEquals(trajectoryName, result.getFileName());
        assertEquals(horizon, result.getHorizon());
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
        verify(efficiencyMeRepository, atLeastOnce()).save(any(EfficiencyMeEntity.class));
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_withNullHorizon_throwsException() throws IOException {
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb("trajectory", null);
        });

        assertEquals("Horizon must not be null", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_withLongTrajectoryName_throwsException() throws IOException {
        // Given
        String longName = "a".repeat(41);
        
        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb(longName, "2023-2024");
        });

        assertEquals("Trajectory name cannot exceed 40 characters", exception.getMessage());
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_withoutCurrentUser_throwsException() throws IOException {
        // Given
        when(userService.getCurrentUserDetails()).thenReturn(null);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb("trajectory", "2023-2024");
        });

        assertEquals("User NNI could not be determined", exception.getMessage());
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_firstTimeUpload_success() throws IOException {
        // Given
        String trajectoryName = "first_upload";
        String horizon = "2023-2024";
        createTestExcelFile(trajectoryName, horizon);

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .version(1)
                .type(TrajectoryType.EFFICIENCY_ME.name())
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenReturn(newTrajectory);

        // When
        TrajectoryEntity result = service.saveEfficiencyMeTrajectoryInDb(trajectoryName, horizon);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getVersion());
        verify(trajectoryRepository, times(1)).save(argThat(t -> t.getVersion() == 1));
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_createNewVersion_success() throws IOException {
        // Given
        String trajectoryName = "test_trajectory";
        String horizon = "2023-2024";
        createTestExcelFile(trajectoryName, horizon);

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryName)
                .horizon(horizon)
                .version(1)
                .checksum("old_checksum")
                .build();

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(2)
                .fileName(trajectoryName)
                .horizon(horizon)
                .version(2)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.of(existingTrajectory));
        when(trajectoryRepository.save(any())).thenReturn(newTrajectory);

        // When
        TrajectoryEntity result = service.saveEfficiencyMeTrajectoryInDb(trajectoryName, horizon);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getVersion());
        verify(trajectoryRepository, times(1)).save(argThat(t -> t.getVersion() == 2));
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_missingHorizonSheet_throwsException() throws IOException {
        // Given
        String trajectoryName = "test_trajectory";
        String horizon = "2023-2024";
        createTestExcelFileWithoutHorizonSheet(trajectoryName, horizon);

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb(trajectoryName, horizon);
        });

        assertTrue(exception.getMessage().contains("Missing horizon"));
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_emptySheet_throwsException() throws IOException {
        // Given
        String trajectoryName = "test_trajectory";
        String horizon = "2023-2024";
        createEmptyExcelFile(trajectoryName);

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb(trajectoryName, horizon);
        });

        assertTrue(exception.getMessage().contains("Missing horizon"));
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_nodeClusterTooLong_throwsException() throws IOException {
        // Given
        String trajectoryName = "test_trajectory";
        String horizon = "2023-2024";
        createExcelFileWithLongNodeCluster(trajectoryName, horizon);

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb(trajectoryName, horizon);
        });

        assertTrue(exception.getMessage().contains("Node/Cluster cannot exceed 60 characters"));
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_missingEfficiencyValue_throwsException() throws IOException {
        // Given
        String trajectoryName = "test_trajectory";
        String horizon = "2023-2024";
        createExcelFileWithMissingEfficiency(trajectoryName, horizon);

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb(trajectoryName, horizon);
        });

        assertTrue(exception.getMessage().contains("Column efficiency must be numeric"));
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_orphanedDataInRow_throwsException() throws IOException {
        // Given
        String trajectoryName = "test_trajectory";
        String horizon = "2023-2024";
        createExcelFileWithOrphanedData(trajectoryName, horizon);

        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb(trajectoryName, horizon);
        });

        assertTrue(exception.getMessage().contains("Node/Cluster column must be filled"));
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_pathSecurityViolation_throwsException() throws IOException {
        // Given
        String trajectoryName = "../../../etc/passwd";

        // When & Then
        assertThrows(IOException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb(trajectoryName, "2023-2024");
        });
    }

    @Test
    void saveEfficiencyMeTrajectoryInDb_incompletePathConfiguration_throwsException() throws IOException {
        // Given
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(null);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.saveEfficiencyMeTrajectoryInDb("trajectory", "2023-2024");
        });

        assertEquals("Antares path configuration is incomplete", exception.getMessage());
    }

    // Helper methods to create test Excel files
    private Path createTestExcelFile(String fileName, String horizon) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        String horizonYear = horizon.split("-")[1];
        Sheet sheet = workbook.createSheet(horizonYear);

        // Create header
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node/Cluster");
        headerRow.createCell(1).setCellValue("Type");
        headerRow.createCell(2).setCellValue("Comments");
        headerRow.createCell(3).setCellValue("Efficiency");

        // Create data row
        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("Node1");
        dataRow.createCell(1).setCellValue("Type1");
        dataRow.createCell(2).setCellValue("Comments");
        dataRow.createCell(3).setCellValue(0.95);

        Path filePath = trajectoryFilePath.resolve(fileName + ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            workbook.write(fos);
        }
        workbook.close();
        return filePath;
    }

    private Path createTestExcelFileWithoutHorizonSheet(String fileName, String horizon) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("WrongSheetName");
        
        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node/Cluster");

        Path filePath = trajectoryFilePath.resolve(fileName + ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            workbook.write(fos);
        }
        workbook.close();
        return filePath;
    }

    private Path createEmptyExcelFile(String fileName) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        workbook.createSheet("2024");

        Path filePath = trajectoryFilePath.resolve(fileName + ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            workbook.write(fos);
        }
        workbook.close();
        return filePath;
    }

    private Path createExcelFileWithLongNodeCluster(String fileName, String horizon) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        String horizonYear = horizon.split("-")[1];
        Sheet sheet = workbook.createSheet(horizonYear);

        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node/Cluster");

        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("a".repeat(61));
        dataRow.createCell(3).setCellValue(0.95);

        Path filePath = trajectoryFilePath.resolve(fileName + ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            workbook.write(fos);
        }
        workbook.close();
        return filePath;
    }

    private Path createExcelFileWithMissingEfficiency(String fileName, String horizon) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        String horizonYear = horizon.split("-")[1];
        Sheet sheet = workbook.createSheet(horizonYear);

        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node/Cluster");

        var dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("Node1");
        dataRow.createCell(3).setCellValue("not_numeric");

        Path filePath = trajectoryFilePath.resolve(fileName + ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            workbook.write(fos);
        }
        workbook.close();
        return filePath;
    }

    private Path createExcelFileWithOrphanedData(String fileName, String horizon) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        String horizonYear = horizon.split("-")[1];
        Sheet sheet = workbook.createSheet(horizonYear);

        var headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Node/Cluster");

        var dataRow = sheet.createRow(1);
        dataRow.createCell(1).setCellValue("Type1");
        dataRow.createCell(3).setCellValue(0.95);

        Path filePath = trajectoryFilePath.resolve(fileName + ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            workbook.write(fos);
        }
        workbook.close();
        return filePath;
    }
}
