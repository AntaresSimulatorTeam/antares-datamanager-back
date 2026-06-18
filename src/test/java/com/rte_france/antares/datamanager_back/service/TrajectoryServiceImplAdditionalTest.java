package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.FsTrajectoryDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Additional tests for TrajectoryServiceImpl
 */
@ExtendWith(MockitoExtension.class)
class TrajectoryServiceImplAdditionalTest {

    @Mock
    private StudyTrajectoryRepository studyTrajectoryRepository;

    @InjectMocks
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private LoadRepository loadRepository;

    @Mock
    private UserService userService;

    @Mock
    private AntaresDataManagerProperties antaresDataManagerProperties;

    @Mock
    private LoadFileProcessorServiceImpl loadFileProcessorServiceImpl;


    @Captor
    private ArgumentCaptor<List<String>> listCustomLoadFilesCaptor;


    @Test
    void isStudyTrajectoryExistById_shouldReturnTrue_whenSecondTrajectoryIsNull() {
        // Given
        Integer studyId = 1;
        WarningMessageEntity warning = WarningMessageEntity.builder()
                .secondTrajectory(null)
                .build();

        // When
        boolean result = trajectoryService.isStudyTrajectoryExistById(studyId, warning);

        // Then
        assertTrue(result);
        verify(studyTrajectoryRepository, never()).findById(any());
    }

    @Test
    void isStudyTrajectoryExistById_shouldReturnTrue_whenStudyTrajectoryExists() {
        // Given
        Integer studyId = 1;
        TrajectoryEntity secondTrajectory = TrajectoryEntity.builder()
                .id(2)
                .build();

        WarningMessageEntity warning = WarningMessageEntity.builder()
                .secondTrajectory(secondTrajectory)
                .build();

        StudyTrajectoryKey key = StudyTrajectoryKey.builder()
                .trajectoryId(2)
                .scenarioId(studyId)
                .build();

        when(studyTrajectoryRepository.findById(key)).thenReturn(Optional.of(new StudyTrajectoryEntity()));

        // When
        boolean result = trajectoryService.isStudyTrajectoryExistById(studyId, warning);

        // Then
        assertTrue(result);
        verify(studyTrajectoryRepository).findById(key);
    }

    @Test
    void isStudyTrajectoryExistById_shouldReturnFalse_whenStudyTrajectoryDoesNotExist() {
        // Given
        Integer studyId = 1;
        TrajectoryEntity secondTrajectory = TrajectoryEntity.builder()
                .id(2)
                .build();

        WarningMessageEntity warning = WarningMessageEntity.builder()
                .secondTrajectory(secondTrajectory)
                .build();

        StudyTrajectoryKey key = StudyTrajectoryKey.builder()
                .trajectoryId(2)
                .scenarioId(studyId)
                .build();

        when(studyTrajectoryRepository.findById(key)).thenReturn(Optional.empty());

        // When
        boolean result = trajectoryService.isStudyTrajectoryExistById(studyId, warning);

        // Then
        assertFalse(result);
        verify(studyTrajectoryRepository).findById(key);
    }

    @Test
    void buildAndSaveLoadTrajectory_shouldCreateListOfCustomLoadFilesWhenAreaIsOTHERS(@TempDir Path tempDir) throws IOException {
        // Given
        String area = "OTHERS";
        String trajectoryToUse = "testTrajectory";
        String horizon = "2030-2031";
        Integer studyId = 1;


        Path trajectoryPath = tempDir.resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);
        Files.createFile(trajectoryPath.resolve("load_fr_2030-2031.txt"));
        Files.createFile(trajectoryPath.resolve("load_de_2030-2031.txt"));

        TrajectoryEntity trajectory1 = TrajectoryEntity.builder()
                .id(1)
                .area("FR")
                .type(TrajectoryType.LOAD.name())
                .build();

        TrajectoryEntity trajectory2 = TrajectoryEntity.builder()
                .id(2)
                .area("DE")
                .type(TrajectoryType.LOAD.name())
                .build();

        TrajectoryEntity trajectory3 = TrajectoryEntity.builder()
                .id(3)
                .area("OTHERS")
                .type(TrajectoryType.LOAD.name())
                .build();

        when(trajectoryRepository.findByTypeAndStudyId(TrajectoryType.LOAD.name(), studyId))
                .thenReturn(Arrays.asList(trajectory1, trajectory2, trajectory3));

        when(areaRepository.findAllByStudyId(studyId))
                .thenReturn(Arrays.asList(
                        AreaEntity.builder().name("FR").build(),
                        AreaEntity.builder().name("DE").build()
                ));

        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("testUser").build());

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getLoadDirectory()).thenReturn("");

        when(loadFileProcessorServiceImpl.checkForMissingLoadFiles(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptySet());

        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));


        try (var mockedStatic = mockStatic(Utils.class)) {
            mockedStatic.when(() -> Utils.getValidLoadFileNamesWithHorizon(
                    any(Path.class),
                    eq(area),
                    eq(horizon),
                    listCustomLoadFilesCaptor.capture(),
                    anyList()
            )).thenReturn(Arrays.asList("load_fr_2030-2031.txt", "load_de_2030-2031.txt"));

            // When
            TrajectoryEntity result = trajectoryService.saveLoadTrajectoriesInDb(area, trajectoryToUse, horizon, studyId);

            // Then
            assertNotNull(result);

            // Verify that the list of custom load files already chosen contains the expected values
            List<String> capturedList = listCustomLoadFilesCaptor.getValue();
            assertEquals(2, capturedList.size());
            assertTrue(capturedList.contains("fr"));
            assertTrue(capturedList.contains("de"));
            assertFalse(capturedList.contains("others"));

            // Verify that trajectoryRepository.findByTypeAndStudyId was called with the correct parameters
            verify(trajectoryRepository).findByTypeAndStudyId(TrajectoryType.LOAD.name(), studyId);
        }
    }

    @Test
    void saveLoadTrajectoriesInDb_throwsBadRequestWhenParamsAreNull() {
        var ex = assertThrows(BusinessException.class, () ->
                trajectoryService.saveLoadTrajectoriesInDb(null, "trajectory", "2023-2024", 1)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("must not be null"));

        ex = assertThrows(BusinessException.class, () ->
                trajectoryService.saveLoadTrajectoriesInDb("area", null, "2023-2024", 1)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());

        ex = assertThrows(BusinessException.class, () ->
                trajectoryService.saveLoadTrajectoriesInDb("area", "trajectory", null, 1)
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void saveLoadTrajectoriesInDb_throwsBadRequestWhenTrajectoryAlreadyUploaded() {
        var area = "FR";
        var trajectoryToUse = "testTrajectory";
        var horizon = "2023-2024";
        var studyId = 1;

        when(userService.getCurrentUserDetails())
                .thenReturn(UserInfoDto.builder().nni("user").build());

        when(areaRepository.findAreaByNameAndStudyId(area, studyId))
                .thenReturn(Optional.of(AreaEntity.builder().name(area).build()));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("/tmp");
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getLoadDirectory()).thenReturn("");

        var existingTrajectory = TrajectoryEntity.builder()
                .fileName(trajectoryToUse)
                .horizon(horizon)
                .area(area)
                .build();
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndAreaOrderByVersionDesc(trajectoryToUse, horizon, area))
                .thenReturn(Optional.of(existingTrajectory));

        try (var utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.isSameTrajectory(any(), eq(existingTrajectory))).thenReturn(true);

            var ex = assertThrows(BusinessException.class, () ->
                    trajectoryService.saveLoadTrajectoriesInDb(area, trajectoryToUse, horizon, studyId)
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
            assertTrue(ex.getMessage().contains("File already processed"));
        }
    }

    @Test
    void unlinkAllTrajectoriesFromStudy_deletesLinksWhenFound() {
        var studyId = 1;
        var links = List.of(
                StudyTrajectoryEntity.builder().build()
        );
        when(studyTrajectoryRepository.findById_ScenarioId(studyId)).thenReturn(links);

        assertDoesNotThrow(() -> trajectoryService.unlinkAllTrajectoriesFromStudy(studyId));
        verify(studyTrajectoryRepository).deleteAll(links);
    }

    @Test
    void unlinkAllTrajectoriesFromStudy_throwsExceptionWhenNoLinksFound() {
        var studyId = 999;
        when(studyTrajectoryRepository.findById_ScenarioId(studyId)).thenReturn(Collections.emptyList());

        var ex = assertThrows(BusinessException.class, () ->
                trajectoryService.unlinkAllTrajectoriesFromStudy(studyId));
        assertEquals("No links found", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void unlinkBatch_success_whenDeletedCountEqualsRequested() {
        var studyId = 1;
        var trajectoryIds = List.of(1, 2, 3);

        when(studyTrajectoryRepository.deleteByStudyIdAndTrajectoryIds(studyId, List.of(1,2,3)))
                .thenReturn(3);

        assertDoesNotThrow(() ->
                trajectoryService.unlinkBatchTrajectoriesFromStudy(studyId, trajectoryIds)
        );

        verify(studyTrajectoryRepository).deleteByStudyIdAndTrajectoryIds(studyId, List.of(1,2,3));
    }

    @Test
    void unlinkBatch_throwsBusinessConflict_whenPartialDeletion() {
        var studyId = 1;
        var trajectoryIds = List.of(1, 2, 3);

        when(studyTrajectoryRepository.deleteByStudyIdAndTrajectoryIds(studyId, List.of(1,2,3)))
                .thenReturn(2);

        var ex = assertThrows(BusinessException.class, () ->
                trajectoryService.unlinkBatchTrajectoriesFromStudy(studyId, trajectoryIds)
        );
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    void unlinkBatch_throwsBadRequest_whenEmptyAfterFilter() {
        var studyId = 1;

        var ex = assertThrows(BusinessException.class, () ->
                trajectoryService.unlinkBatchTrajectoriesFromStudy(studyId, Arrays.asList(null, null))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());

        verifyNoInteractions(studyTrajectoryRepository);
    }

    @Test
    void unlinkBatch_throwsNPE_whenListIsNull() {
        var studyId = 1;
        assertThrows(NullPointerException.class, () ->
                trajectoryService.unlinkBatchTrajectoriesFromStudy(studyId, null)
        );
        verifyNoInteractions(studyTrajectoryRepository);
    }

    @Test
    void findTrajectoriesByType_returnsEmptyList(@TempDir Path tempDir) throws IOException {
        // Given
        Path linkDir = tempDir.resolve("link");
        Files.createDirectories(linkDir);

        Path testFile = linkDir.resolve("invalid_name.txt");
        Files.createFile(testFile);

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getLinkDirectory()).thenReturn("link");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.LINK,null, "OTHER", null);

        // Then
        assertEquals(0, result.size());
    }

    @Test
    void findTrajectoriesByType_returnsFilesStartingByAreas_(@TempDir Path tempDir) throws IOException {
        // Given
        Path areaDir = tempDir.resolve("area");
        Files.createDirectories(areaDir);

        Path testFile = areaDir.resolve("areas_test1.xlsx");
        Files.createFile(testFile);

        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getAreaDirectory()).thenReturn("area");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.AREA, null,null, null);

        // Then
        assertEquals(1, result.size());
        assertEquals("areas_test1.xlsx", result.getFirst().getFileName());
    }

    @Test
    void findTrajectoriesByType_returnsSpecificFilesForThermalTechnicalCommonParameter(@TempDir Path tempDir) throws IOException {
        Path thermalDir = tempDir.resolve("thermal");
        Files.createDirectories(thermalDir);

        Path specificFile = thermalDir.resolve("specific_param_test.xlsx");
        Path commonFile = thermalDir.resolve("common_param_test.xlsx");
        Files.createFile(specificFile);
        Files.createFile(commonFile);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getThermalParameterDirectory()).thenReturn("thermal");

        java.util.List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.THERMAL_TECHNICAL_COMMON_PARAMETER, null, null,null);

        assertEquals(1, result.size());
        assertTrue(result.getFirst().getFileName().startsWith("common_param_"));
    }

    @Test
    void findTrajectoriesByType_returnsSpecificFilesForThermalTechnicalSpecificParameter(@TempDir Path tempDir) throws IOException {
        Path thermalDir = tempDir.resolve("thermal");
        Files.createDirectories(thermalDir);

        Path specificFile = thermalDir.resolve("specific_param_file.xlsx");
        Path commonFile = thermalDir.resolve("common_param_file.xlsx");
        Files.createFile(specificFile);
        Files.createFile(commonFile);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getThermalParameterDirectory()).thenReturn("thermal");

        java.util.List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.THERMAL_TECHNICAL_SPECIFIC_PARAMETER, null, null,null);

        assertEquals(1, result.size());
        assertTrue(result.getFirst().getFileName().startsWith("specific_param_"));
    }

    @Test
    void findTrajectoriesByTypeAndFileNameStartWithFromFS_returnsFileNamesWhenDirectoryExists(@TempDir Path tempDir) throws IOException {
        // Given
        Path areaDir = tempDir.resolve("area");
        Files.createDirectories(areaDir);

        // When
        Path testFile = areaDir.resolve("areas_testFile.xlsx");
        Files.createFile(testFile);


        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn("");
        when(antaresDataManagerProperties.getAreaDirectory()).thenReturn("area");

        // Then
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.AREA, null,null,"test");

        assertEquals(1, result.size());
        assertEquals("areas_testFile.xlsx", result.getFirst().getFileName());
    }


    @Test
    void testFindTrajectoriesByType_ModulationDirectories(@TempDir Path tempDir) throws Exception {

        Path dir1 = tempDir.resolve("ModulationFR");
        Files.createDirectories(dir1);
        Path dir2 = tempDir.resolve("ModulationPEMMDB");
        Files.createDirectories(dir2);


        Files.createFile(dir1.resolve("CM_test.txt"));
        Files.createFile(dir2.resolve("MR_test.txt"));
        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn(""); // empty if not used
        when(antaresDataManagerProperties.getThermalModulationParameterDirectory()).thenReturn(""); // important!

        // Act
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(
                TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER,
                tempDir.toString(),
                null,
                null
        );

        assertEquals(2, result.size(), "Should return 2 directories as DTOs");

        assertTrue(result.stream().anyMatch(dto -> dto.getFileName().equals("ModulationFR")));
        assertTrue(result.stream().anyMatch(dto -> dto.getFileName().equals("ModulationPEMMDB")));
        assertTrue(result.stream().allMatch(dto -> dto.getLastModifiedDate() != null));
        assertTrue(result.stream().allMatch(dto -> dto.getType().equals("THERMAL_TECHNICAL_MODULATION_PARAMETER")));
    }

    // ==================== NUCLEAR TRAJECTORIES TESTS ====================

    @Test
    void findTrajectoriesByType_returnsNuclearModulationDirectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/modulation");
        Files.createDirectories(nuclearDir);
        
        // Create subdirectories as modulation trajectories are directories
        Path mod2023 = nuclearDir.resolve("modulation_2023-2024");
        Path mod2024 = nuclearDir.resolve("modulation_2024-2025");
        Files.createDirectory(mod2023);
        Files.createDirectory(mod2024);
        
        // Add files inside to make directories non-empty
        Files.createFile(mod2023.resolve("data.txt"));
        Files.createFile(mod2024.resolve("data.txt"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn("nuclear/modulation");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_MODULATION, null, null, null);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> dto.getFileName().equals("modulation_2023-2024")));
        assertTrue(result.stream().anyMatch(dto -> dto.getFileName().equals("modulation_2024-2025")));
        assertTrue(result.stream().allMatch(dto -> dto.getType().equals("NUCLEAR_FR_MODULATION")));
    }

    @Test
    void findTrajectoriesByType_returnsNuclearLtDirectories(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/lt");
        Files.createDirectories(nuclearDir);
        
        // Create subdirectories as lt trajectories are directories
        Path lt2023 = nuclearDir.resolve("lt_2023-2024");
        Path lt2024 = nuclearDir.resolve("lt_2024-2025");
        Files.createDirectory(lt2023);
        Files.createDirectory(lt2024);
        
        // Add files inside to make directories non-empty
        Files.createFile(lt2023.resolve("data.txt"));
        Files.createFile(lt2024.resolve("data.txt"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearLtDirectory()).thenReturn("nuclear/lt");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_TS_LONG_TERM, null, null, null);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(dto -> dto.getFileName().equals("lt_2023-2024")));
        assertTrue(result.stream().anyMatch(dto -> dto.getFileName().equals("lt_2024-2025")));
        assertTrue(result.stream().allMatch(dto -> dto.getType().equals("NUCLEAR_FR_TS_LONG_TERM")));
    }


    @Test
    void findTrajectoriesByType_emptyNuclearModulationDirectory(@TempDir Path tempDir) throws IOException {
        // Given
        Path nuclearDir = tempDir.resolve("nuclear/modulation");
        Files.createDirectories(nuclearDir);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn("nuclear/modulation");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(TrajectoryType.NUCLEAR_FR_MODULATION, null, null, null);

        // Then
        assertTrue(result.isEmpty(), "Should return empty list for empty directory");
    }

    // ==================== TEST FOR IOException HANDLING IN isDirectoryEmpty ====================

    @Test
    void isDirectoryEmpty_withInvalidPath_returnsTrue() {
        // Given - a non-existent path
        Path invalidPath = Path.of("/non/existent/path/that/does/not/exist");

        // When - calling isDirectoryEmpty with invalid path
        boolean result = trajectoryService.isDirectoryEmpty(invalidPath);

        // Then - should return true (treats inaccessible directory as empty)
        assertTrue(result, "Should return true when directory cannot be read (IOException)");
    }

    @Test
    void isDirectoryEmpty_withPermissionDenied_returnsTrue() throws IOException {
        // Given - a directory (on Unix systems) with no read permissions
        Path tempDir = Files.createTempDirectory("test_no_read_");
        try {
            // Remove read permission (this only works on Unix-like systems)
            tempDir.toFile().setReadable(false);

            // When - calling isDirectoryEmpty with restricted directory
            boolean result = trajectoryService.isDirectoryEmpty(tempDir);

            // Then - should return true (treats permission denied as empty)
            assertTrue(result, "Should return true when directory cannot be read due to permissions");
        } finally {
            // Cleanup - restore permissions and delete
            try {
                tempDir.toFile().setReadable(true);
                Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    void isDirectoryEmpty_withEmptyDirectory_returnsFalse(@TempDir Path tempDir) {
        // Given - an empty directory
        
        // When - calling isDirectoryEmpty
        boolean result = trajectoryService.isDirectoryEmpty(tempDir);

        // Then - should return true (directory is empty)
        assertTrue(result, "Should return true for empty directory");
    }

    @Test
    void isDirectoryEmpty_withNonEmptyDirectory_returnsFalse(@TempDir Path tempDir) throws IOException {
        // Given - a directory with a file
        Files.createFile(tempDir.resolve("test.txt"));

        // When - calling isDirectoryEmpty
        boolean result = trajectoryService.isDirectoryEmpty(tempDir);

        // Then - should return false (directory is not empty)
        assertFalse(result, "Should return false for non-empty directory");
    }

    @Test
    void findTrajectoriesByType_withEmptyDirectoryTrajectory_skipsDirectory(@TempDir Path tempDir) throws IOException {
        // Given - a nuclear modulation directory with empty subdirectories
        Path nuclearDir = tempDir.resolve("nuclear/modulation");
        Files.createDirectories(nuclearDir);
        
        // Create empty subdirectories
        Path emptyMod1 = nuclearDir.resolve("empty_modulation_1");
        Path emptyMod2 = nuclearDir.resolve("empty_modulation_2");
        Files.createDirectory(emptyMod1);
        Files.createDirectory(emptyMod2);

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn("nuclear/modulation");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(
                TrajectoryType.NUCLEAR_FR_MODULATION, null, null, null);

        // Then - empty directories should be skipped (not included in results)
        // because isDirectoryEmpty returns true and isDirectoryTrajectory checks !isDirectoryEmpty(path)
        assertTrue(result.isEmpty(), "Should skip empty directory trajectories");
    }

    @Test
    void findTrajectoriesByType_mixedEmptyAndNonEmptyDirectories_returnsOnlyNonEmpty(@TempDir Path tempDir) throws IOException {
        // Given - a directory with both empty and non-empty subdirectories
        Path nuclearDir = tempDir.resolve("nuclear/modulation");
        Files.createDirectories(nuclearDir);
        
        // Create empty directory
        Path emptyMod = nuclearDir.resolve("empty_modulation");
        Files.createDirectory(emptyMod);
        
        // Create non-empty directory
        Path nonEmptyMod = nuclearDir.resolve("modulation_2023-2024");
        Files.createDirectory(nonEmptyMod);
        Files.createFile(nonEmptyMod.resolve("data.txt"));

        when(antaresDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaresDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaresDataManagerProperties.getNuclearModulationDirectory()).thenReturn("nuclear/modulation");

        // When
        List<FsTrajectoryDTO> result = trajectoryService.findTrajectoriesByType(
                TrajectoryType.NUCLEAR_FR_MODULATION, null, null, null);

        // Then - only non-empty directory should be returned
        assertEquals(1, result.size(), "Should return only non-empty directories");
        assertEquals("modulation_2023-2024", result.getFirst().getFileName(),
                "Should return the non-empty directory");
    }

}

