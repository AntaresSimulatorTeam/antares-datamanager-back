package com.rte_france.antares.datamanager_back.service.settings;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsImportServiceImportSettingsTest {

    @Mock
    private SettingsGeneralParametersRepository generalParametersRepository;

    @Mock
    private SettingsOptimizationParametersRepository optimizationParametersRepository;

    @Mock
    private SettingsAdvancedParametersRepository advancedParametersRepository;

    @Mock
    private SettingsSeedsParametersRepository seedsParametersRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    private SettingsImportService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new SettingsImportService(
                generalParametersRepository,
                optimizationParametersRepository,
                advancedParametersRepository,
                seedsParametersRepository,
                trajectoryRepository,
                antaresDataManagerProperties
        );
    }

    // Helper method to create a test Excel file
    private File createTestExcelFile(Path directory) throws IOException {
        return createTestExcelFile(directory, "BP23_test.xlsx");
    }

    private File createTestExcelFile(Path directory, String fileName) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        
        // Create General parameters sheet
        var sheet1 = workbook.createSheet("General parameters");
        var row1 = sheet1.createRow(0);
        row1.createCell(0).setCellValue("Mode");
        row1.createCell(1).setCellValue("Adequacy");

        // Create Optimization preferences sheet
        var sheet2 = workbook.createSheet("Optimization preferences");
        var row2 = sheet2.createRow(0);
        row2.createCell(0).setCellValue("simplex optimization range");
        row2.createCell(1).setCellValue("week");

        // Create Advanced parameters sheet
        var sheet3 = workbook.createSheet("Advanced parameters");
        var row3 = sheet3.createRow(0);
        row3.createCell(0).setCellValue("hydro heuristic policy");
        row3.createCell(1).setCellValue("middleages");

        File file = directory.resolve(fileName).toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
        workbook.close();
        return file;
    }

    @Test
    void testImportSettingsSuccessfulFirstImport() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);
        createTestExcelFile(settingsDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(1)
                .type("SETTINGS")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "SETTINGS", horizon, area
        )).thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = service.importSettings(trajectoryToUse, horizon, studyId, area);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getVersion());
        assertEquals(trajectoryToUse, result.getFileName());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
        verify(generalParametersRepository).save(any());
    }

    @Test
    void testImportSettingsWithExistingTrajectoryNewVersion() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);
        createTestExcelFile(settingsDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Existing trajectory with version 1 and different checksum
        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(1)
                .checksum("old_checksum")
                .type("SETTINGS")
                .build();

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(2)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(2)
                .type("SETTINGS")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "SETTINGS", horizon, area
        )).thenReturn(Optional.of(existingTrajectory));

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(newTrajectory);

        // Act
        TrajectoryEntity result = service.importSettings(trajectoryToUse, horizon, studyId, area);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getVersion());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void testImportSettingsDuplicateChecksumThrowsException() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);
        File excelFile = createTestExcelFile(settingsDir);

        // Calculate the same combined checksum the service will compute
        String fileChecksum;
        try (var fis = new java.io.FileInputStream(excelFile);
             Workbook wb = new XSSFWorkbook(fis)) {
            fileChecksum = service.calculateCombinedChecksum(service.calculateSheetChecksums(wb));
        }

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Existing trajectory with same checksum
        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(1)
                .checksum(fileChecksum)
                .type("SETTINGS")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "SETTINGS", horizon, area
        )).thenReturn(Optional.of(existingTrajectory));

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        assertTrue(exception.getMessage().contains("already processed with same checksum"));
    }

    @Test
    void testImportSettingsFolderNotFoundThrowsException() {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("nonexistent/directory");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        assertTrue(exception.getMessage().contains("Trajectory settings folder not found"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void testImportSettingsFileNotFoundThrowsException() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        assertTrue(exception.getMessage().contains("Settings file not found"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void testImportSettingsInvalidExcelFileThrowsException() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);

        // Create an invalid Excel file
        File invalidFile = settingsDir.resolve("BP23_test.xlsx").toFile();
        try (FileOutputStream fos = new FileOutputStream(invalidFile)) {
            fos.write("This is not a valid Excel file".getBytes());
        }

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert
        assertThrows(Exception.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });
    }

    @Test
    void testImportSettingsCallsAllImportMethods() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);
        createTestExcelFile(settingsDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(1)
                .type("SETTINGS")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "SETTINGS", horizon, area
        )).thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = service.importSettings(trajectoryToUse, horizon, studyId, area);

        // Assert
        assertNotNull(result);
        
        // Verify all import methods are called
        verify(generalParametersRepository).save(any());
        verify(optimizationParametersRepository).save(any());
        verify(advancedParametersRepository).save(any());
        verify(seedsParametersRepository).save(any());
    }

    @Test
    void testImportSettingsReturnsSavedTrajectory() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);
        createTestExcelFile(settingsDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(42)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(1)
                .type("SETTINGS")
                .hasTimeSeries(false)
                .build();

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "SETTINGS", horizon, area
        )).thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = service.importSettings(trajectoryToUse, horizon, studyId, area);

        // Assert
        assertNotNull(result);
        assertEquals(42, result.getId());
        assertEquals(trajectoryToUse, result.getFileName());
        assertEquals(horizon, result.getHorizon());
        assertEquals(area, result.getArea());
        assertEquals(1, result.getVersion());
        assertFalse(result.getHasTimeSeries());
    }

    @Test
    void testImportSettingsWithMultipleVersions() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);
        createTestExcelFile(settingsDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Simulate 3 existing versions
        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(3)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(3)
                .checksum("old_checksum")
                .type("SETTINGS")
                .build();

        TrajectoryEntity newTrajectory = TrajectoryEntity.builder()
                .id(4)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(4)
                .type("SETTINGS")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "SETTINGS", horizon, area
        )).thenReturn(Optional.of(existingTrajectory));

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(newTrajectory);

        // Act
        TrajectoryEntity result = service.importSettings(trajectoryToUse, horizon, studyId, area);

        // Assert
        assertEquals(4, result.getVersion());
    }

    @Test
    void testImportSettingsPathNormalization() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_test";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);
        createTestExcelFile(settingsDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(1)
                .type("SETTINGS")
                .build();

        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "SETTINGS", horizon, area
        )).thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(expectedTrajectory);

        // Act - should not throw any path normalization errors
        TrajectoryEntity result = service.importSettings(trajectoryToUse, horizon, studyId, area);

        // Assert
        assertNotNull(result);
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void testImportSettingsCaseInsensitiveSearch() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_TEST";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "fr";

        Path settingsDir = tempDir.resolve("trajectories/parameters/general_data");
        Files.createDirectories(settingsDir);
        createTestExcelFile(settingsDir, "BP23_TEST.xlsx");

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .version(1)
                .type("SETTINGS")
                .build();

        // Verify case-insensitive search is called
        when(trajectoryRepository.findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "SETTINGS", horizon, area
        )).thenReturn(Optional.empty());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(expectedTrajectory);

        // Act
        TrajectoryEntity result = service.importSettings(trajectoryToUse, horizon, studyId, area);

        // Assert
        assertNotNull(result);
        verify(trajectoryRepository).findFirstByFileNameAndTypeAndHorizonAndAreaIgnoreCaseOrderByVersionDesc(
                trajectoryToUse, "SETTINGS", horizon, area
        );
    }
}
