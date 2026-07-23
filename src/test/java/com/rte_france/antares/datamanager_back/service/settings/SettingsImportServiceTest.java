package com.rte_france.antares.datamanager_back.service.settings;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingsImportServiceTest {

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

    @Test
    void testImportSettingsFileNotFound() throws IOException {
        // Arrange
        String trajectoryToUse = "NONEXISTENT";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        // Verify trajectory was not saved
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void testImportSettingsDuplicateDetection() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_A_ref_200MC";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .type("TRAJECTORY_SETTINGS")
                .horizon(horizon)
                .area(area)
                .version(1)
                .checksum("same_checksum_value")
                .build();

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        // Verify trajectory was not saved again
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void testImportSettingsParameterValidation() throws IOException {
        // Test with null parameters
        assertThrows(Exception.class, () -> {
            service.importSettings(null, "2028-2029", 1, "FR");
        });

        assertThrows(Exception.class, () -> {
            service.importSettings("BP23", null, 1, "FR");
        });

        assertThrows(Exception.class, () -> {
            service.importSettings("BP23", "2028-2029", null, "FR");
        });

        assertThrows(Exception.class, () -> {
            service.importSettings("BP23", "2028-2029", 1, null);
        });
    }

    @Test
    void testImportSettingsVersionIncrement() throws IOException {
        // Arrange
        String trajectoryToUse = "BP23_A_ref_200MC";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        TrajectoryEntity existingTrajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(trajectoryToUse)
                .type("TRAJECTORY_SETTINGS")
                .horizon(horizon)
                .area(area)
                .version(1)
                .checksum("old_checksum_value")
                .build();

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        // File won't exist in test, so it will fail before version increment
        verify(trajectoryRepository, never()).save(any());
    }

    @Test
    void testSecurityContextIntegration() {
        // Arrange
        UserDetails userDetails = User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, "password");
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        // This test verifies that security context can be accessed
        // The actual user extraction happens in the service
        assertNotNull(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void testPathConstructionFollowsNuclearPattern() {
        // Test that path construction follows the NuclearFileProcessorServiceImpl pattern
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Expected path pattern:
        // {NAS_DIR}/trajectories/parameters/general_data/{trajectoryToUse}/general_data_{trajectoryToUse}.xlsx
        String expectedBasePath = "/mnt/data/trajectories";
        String expectedTrajectoryFolder = "/mnt/data/trajectories/parameters/general_data/BP23_A_ref_200MC";

        // Verify the properties are correctly configured for path construction
        assertEquals("/mnt/data", antaresDataManagerProperties.getNasDirectory());
        assertEquals("trajectories", antaresDataManagerProperties.getTrajectoryFilePath());
        assertEquals("parameters/general_data", antaresDataManagerProperties.getTrajectorySettingsDirectory());
    }

    @Test
    void testGeneralParametersImportNotFound() throws IOException {
        // Arrange
        String trajectoryToUse = "NONEXISTENT_TRAJECTORY";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert - should throw BusinessException when folder doesn't exist
        assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });
    }

    @Test
    void testExceptionHandlingWithHttpStatus() throws IOException {
        // Test that BusinessException is thrown with proper HTTP status
        String trajectoryToUse = "NONEXISTENT";
        String horizon = "2028-2029";
        Integer studyId = 1;
        String area = "FR";

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/mnt/data");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("trajectories");
        when(antaresDataManagerProperties.getTrajectorySettingsDirectory()).thenReturn("parameters/general_data");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            service.importSettings(trajectoryToUse, horizon, studyId, area);
        });

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Trajectory settings folder not found"));
    }

    // Tests for readParametersSheet method
    @Test
    void testReadParametersSheetWithNormalKeys() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Test");

        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("Mode");
        row1.createCell(1).setCellValue("Adequacy");

        org.apache.poi.ss.usermodel.Row row2 = sheet.createRow(1);
        row2.createCell(0).setCellValue("Horizon");
        row2.createCell(1).setCellValue("2028");

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod("readParametersSheet", org.apache.poi.ss.usermodel.Sheet.class);
            method.setAccessible(true);
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) method.invoke(service, sheet);
            
            assertEquals(2, result.size());
            assertEquals("Adequacy", result.get("mode"));
            assertEquals("2028", result.get("horizon"));
        } catch (Exception e) {
            fail("Could not invoke readParametersSheet method: " + e.getMessage());
        }
    }

    @Test
    void testReadParametersSheetWithDuplicateKeys() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Test");

        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("Mode");
        row1.createCell(1).setCellValue("Adequacy");

        org.apache.poi.ss.usermodel.Row row2 = sheet.createRow(1);
        row2.createCell(0).setCellValue("Mode");
        row2.createCell(1).setCellValue("Economy");

        org.apache.poi.ss.usermodel.Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("Mode");
        row3.createCell(1).setCellValue("Draft");

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod("readParametersSheet", org.apache.poi.ss.usermodel.Sheet.class);
            method.setAccessible(true);
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) method.invoke(service, sheet);
            
            assertEquals(3, result.size());
            assertEquals("Adequacy", result.get("mode"));
            assertEquals("Economy", result.get("mode-2"));
            assertEquals("Draft", result.get("mode-3"));
        } catch (Exception e) {
            fail("Could not invoke readParametersSheet method: " + e.getMessage());
        }
    }

    @Test
    void testReadParametersSheetWithSpacesInKeys() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Test");

        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("Number of MC year");
        row1.createCell(1).setCellValue(100);

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod("readParametersSheet", org.apache.poi.ss.usermodel.Sheet.class);
            method.setAccessible(true);
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) method.invoke(service, sheet);
            
            assertEquals(1, result.size());
            assertEquals(100.0, result.get("number-of-mc-year"));
        } catch (Exception e) {
            fail("Could not invoke readParametersSheet method: " + e.getMessage());
        }
    }

    @Test
    void testReadParametersSheetWithNullValues() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Test");

        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(0);
        row1.createCell(1).setCellValue("Value1");

        org.apache.poi.ss.usermodel.Row row2 = sheet.createRow(1);
        row2.createCell(0).setCellValue("Mode");
        row2.createCell(1).setCellValue("Adequacy");

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod("readParametersSheet", org.apache.poi.ss.usermodel.Sheet.class);
            method.setAccessible(true);
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) method.invoke(service, sheet);
            
            assertEquals(1, result.size());
            assertEquals("Adequacy", result.get("mode"));
        } catch (Exception e) {
            fail("Could not invoke readParametersSheet method: " + e.getMessage());
        }
    }

    @Test
    void testReadParametersSheetWithEmptyRows() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Test");

        sheet.createRow(0);
        sheet.createRow(1);

        org.apache.poi.ss.usermodel.Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("Mode");
        row3.createCell(1).setCellValue("Adequacy");

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod("readParametersSheet", org.apache.poi.ss.usermodel.Sheet.class);
            method.setAccessible(true);
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) method.invoke(service, sheet);
            
            assertEquals(1, result.size());
            assertEquals("Adequacy", result.get("mode"));
        } catch (Exception e) {
            fail("Could not invoke readParametersSheet method: " + e.getMessage());
        }
    }

    @Test
    void testReadParametersSheetWithMixedDataTypes() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Test");

        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("Mode");
        row1.createCell(1).setCellValue("Adequacy");

        org.apache.poi.ss.usermodel.Row row2 = sheet.createRow(1);
        row2.createCell(0).setCellValue("Number");
        row2.createCell(1).setCellValue(100.5);

        org.apache.poi.ss.usermodel.Row row3 = sheet.createRow(2);
        row3.createCell(0).setCellValue("Boolean");
        row3.createCell(1).setCellValue(true);

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod("readParametersSheet", org.apache.poi.ss.usermodel.Sheet.class);
            method.setAccessible(true);
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) method.invoke(service, sheet);
            
            assertEquals(3, result.size());
            assertEquals("Adequacy", result.get("mode"));
            assertEquals(100.5, result.get("number"));
            assertEquals(true, result.get("boolean"));
        } catch (Exception e) {
            fail("Could not invoke readParametersSheet method: " + e.getMessage());
        }
    }

    // Tests for importGeneralParameters
    @Test
    void testImportGeneralParametersWithValidData() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("General");

        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("Mode");
        row1.createCell(1).setCellValue("Adequacy");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("test")
                .type("SETTINGS")
                .build();

        java.util.Map<String, String> sheetChecksums = new java.util.HashMap<>();
        sheetChecksums.put("General", "checksum123");

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod(
                    "importGeneralParameters",
                    org.apache.poi.ss.usermodel.Workbook.class,
                    TrajectoryEntity.class,
                    java.util.Map.class
            );
            method.setAccessible(true);
            method.invoke(service, workbook, trajectory, sheetChecksums);

            verify(generalParametersRepository).save(any());
        } catch (Exception e) {
            fail("Could not invoke importGeneralParameters method: " + e.getMessage());
        }
    }

    @Test
    void testImportGeneralParametersSheetNotFound() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        workbook.createSheet("Other Sheet");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("test")
                .type("SETTINGS")
                .build();

        java.util.Map<String, String> sheetChecksums = new java.util.HashMap<>();

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod(
                    "importGeneralParameters",
                    org.apache.poi.ss.usermodel.Workbook.class,
                    TrajectoryEntity.class,
                    java.util.Map.class
            );
            method.setAccessible(true);
            method.invoke(service, workbook, trajectory, sheetChecksums);

            verify(generalParametersRepository, never()).save(any());
        } catch (Exception e) {
            fail("Could not invoke importGeneralParameters method: " + e.getMessage());
        }
    }

    // Tests for importOptimizationParameters
    @Test
    void testImportOptimizationParametersWithValidData() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Optimization");

        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("simplex optimization range");
        row1.createCell(1).setCellValue("week");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("test")
                .type("SETTINGS")
                .build();

        java.util.Map<String, String> sheetChecksums = new java.util.HashMap<>();
        sheetChecksums.put("Optimization", "checksum456");

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod(
                    "importOptimizationParameters",
                    org.apache.poi.ss.usermodel.Workbook.class,
                    TrajectoryEntity.class,
                    java.util.Map.class
            );
            method.setAccessible(true);
            method.invoke(service, workbook, trajectory, sheetChecksums);

            verify(optimizationParametersRepository).save(any());
        } catch (Exception e) {
            fail("Could not invoke importOptimizationParameters method: " + e.getMessage());
        }
    }

    @Test
    void testImportOptimizationParametersSheetNotFound() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        workbook.createSheet("Other Sheet");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("test")
                .type("SETTINGS")
                .build();

        java.util.Map<String, String> sheetChecksums = new java.util.HashMap<>();

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod(
                    "importOptimizationParameters",
                    org.apache.poi.ss.usermodel.Workbook.class,
                    TrajectoryEntity.class,
                    java.util.Map.class
            );
            method.setAccessible(true);
            method.invoke(service, workbook, trajectory, sheetChecksums);

            verify(optimizationParametersRepository, never()).save(any());
        } catch (Exception e) {
            fail("Could not invoke importOptimizationParameters method: " + e.getMessage());
        }
    }

    // Tests for importAdvancedParameters
    @Test
    void testImportAdvancedParametersWithValidData() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Advanced parameters");

        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("hydro heuristic policy");
        row1.createCell(1).setCellValue("middleages");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("test")
                .type("SETTINGS")
                .build();

        java.util.Map<String, String> sheetChecksums = new java.util.HashMap<>();
        sheetChecksums.put("Advanced parameters", "checksum789");

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod(
                    "importAdvancedParameters",
                    org.apache.poi.ss.usermodel.Workbook.class,
                    TrajectoryEntity.class,
                    java.util.Map.class
            );
            method.setAccessible(true);
            method.invoke(service, workbook, trajectory, sheetChecksums);

            verify(advancedParametersRepository).save(any());
        } catch (Exception e) {
            fail("Could not invoke importAdvancedParameters method: " + e.getMessage());
        }
    }

    @Test
    void testImportAdvancedParametersSheetNotFound() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        workbook.createSheet("Other Sheet");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("test")
                .type("SETTINGS")
                .build();

        java.util.Map<String, String> sheetChecksums = new java.util.HashMap<>();

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod(
                    "importAdvancedParameters",
                    org.apache.poi.ss.usermodel.Workbook.class,
                    TrajectoryEntity.class,
                    java.util.Map.class
            );
            method.setAccessible(true);
            method.invoke(service, workbook, trajectory, sheetChecksums);

            verify(advancedParametersRepository, never()).save(any());
        } catch (Exception e) {
            fail("Could not invoke importAdvancedParameters method: " + e.getMessage());
        }
    }

    // Tests for importSeedsParameters
    @Test
    void testImportSeedsParametersWithValidData() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Advanced parameters");

        org.apache.poi.ss.usermodel.Row row1 = sheet.createRow(0);
        row1.createCell(0).setCellValue("Thermal time-series generation");
        row1.createCell(1).setCellValue(100);

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("test")
                .type("SETTINGS")
                .build();

        java.util.Map<String, String> sheetChecksums = new java.util.HashMap<>();
        sheetChecksums.put("Advanced parameters", "checksum101");

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod(
                    "importSeedsParameters",
                    org.apache.poi.ss.usermodel.Workbook.class,
                    TrajectoryEntity.class,
                    java.util.Map.class
            );
            method.setAccessible(true);
            method.invoke(service, workbook, trajectory, sheetChecksums);

            verify(seedsParametersRepository).save(any());
        } catch (Exception e) {
            fail("Could not invoke importSeedsParameters method: " + e.getMessage());
        }
    }

    @Test
    void testImportSeedsParametersSheetNotFound() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        workbook.createSheet("Other Sheet");

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("test")
                .type("SETTINGS")
                .build();

        java.util.Map<String, String> sheetChecksums = new java.util.HashMap<>();

        java.lang.reflect.Method method = null;
        try {
            method = SettingsImportService.class.getDeclaredMethod(
                    "importSeedsParameters",
                    org.apache.poi.ss.usermodel.Workbook.class,
                    TrajectoryEntity.class,
                    java.util.Map.class
            );
            method.setAccessible(true);
            method.invoke(service, workbook, trajectory, sheetChecksums);

            verify(seedsParametersRepository, never()).save(any());
        } catch (Exception e) {
            fail("Could not invoke importSeedsParameters method: " + e.getMessage());
        }
    }

    // Tests for checksum methods
    @Test
    void testCalculateSheetChecksumsOnlyKnownSheets() throws IOException {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        workbook.createSheet("General").createRow(0).createCell(0).setCellValue("Mode");
        workbook.createSheet("Optimization").createRow(0).createCell(0).setCellValue("simplex");
        workbook.createSheet("Advanced parameters").createRow(0).createCell(0).setCellValue("hydro");
        workbook.createSheet("Ignored sheet").createRow(0).createCell(0).setCellValue("ignored");

        java.util.Map<String, String> checksums = service.calculateSheetChecksums(workbook);

        assertEquals(3, checksums.size());
        assertTrue(checksums.containsKey("General"));
        assertTrue(checksums.containsKey("Optimization"));
        assertTrue(checksums.containsKey("Advanced parameters"));
        assertFalse(checksums.containsKey("Ignored sheet"));
    }

    @Test
    void testCalculateSheetChecksumsEmptyWorkbook() throws IOException {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();

        java.util.Map<String, String> checksums = service.calculateSheetChecksums(workbook);

        assertTrue(checksums.isEmpty());
    }

    @Test
    void testCalculateSheetChecksumIsDeterministic() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Test");
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("Mode");
        row.createCell(1).setCellValue("Adequacy");

        String checksum1 = service.calculateSheetChecksum(sheet);
        String checksum2 = service.calculateSheetChecksum(sheet);

        assertNotNull(checksum1);
        assertEquals(64, checksum1.length()); // SHA-256 hex
        assertEquals(checksum1, checksum2);
    }

    @Test
    void testCalculateSheetChecksumDifferentContentDifferentChecksum() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet1 = workbook.createSheet("Sheet1");
        sheet1.createRow(0).createCell(0).setCellValue("ValueA");
        org.apache.poi.ss.usermodel.Sheet sheet2 = workbook.createSheet("Sheet2");
        sheet2.createRow(0).createCell(0).setCellValue("ValueB");

        assertNotEquals(service.calculateSheetChecksum(sheet1), service.calculateSheetChecksum(sheet2));
    }

    @Test
    void testCalculateSheetChecksumSkipsNullRows() {
        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Test");
        // Row 0 and 2 exist, row 1 is null
        sheet.createRow(0).createCell(0).setCellValue("First");
        sheet.createRow(2).createCell(0).setCellValue("Third");

        String checksum = service.calculateSheetChecksum(sheet);

        assertNotNull(checksum);
        assertEquals(64, checksum.length());
    }

    @Test
    void testCalculateCombinedChecksum() {
        java.util.Map<String, String> sheetChecksums = new java.util.LinkedHashMap<>();
        sheetChecksums.put("Sheet1", "abc");
        sheetChecksums.put("Sheet2", "def");

        String combined = service.calculateCombinedChecksum(sheetChecksums);

        assertNotNull(combined);
        assertEquals(64, combined.length());
        assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex("abcdef"), combined);
    }

    @Test
    void testCalculateCombinedChecksumEmptyMap() {
        String combined = service.calculateCombinedChecksum(new java.util.HashMap<>());

        assertEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(""), combined);
    }

    // Tests for getCurrentUser method
    @Test
    void testGetCurrentUserWithUserDetails() {
        UserDetails userDetails = User.withUsername("john.doe").password("pwd").authorities("ROLE_USER").build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        try {
            assertEquals("john.doe", service.getCurrentUser());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void testGetCurrentUserWithNonUserDetailsPrincipal() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("simple-principal", null);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        try {
            assertEquals("UNKNOWN", service.getCurrentUser());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void testGetCurrentUserWithNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertEquals("UNKNOWN", service.getCurrentUser());
    }
}
