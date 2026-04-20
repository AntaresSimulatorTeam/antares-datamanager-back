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
}