package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.configuration.AntaressDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.StudyTrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
    private AntaressDataManagerProperties antaressDataManagerProperties;

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
                .loadArea("FR")
                .type(TrajectoryType.LOAD.name())
                .build();

        TrajectoryEntity trajectory2 = TrajectoryEntity.builder()
                .id(2)
                .loadArea("DE")
                .type(TrajectoryType.LOAD.name())
                .build();

        TrajectoryEntity trajectory3 = TrajectoryEntity.builder()
                .id(3)
                .loadArea("OTHERS")
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

        when(antaressDataManagerProperties.getNasDirectory()).thenReturn(tempDir.toString());
        when(antaressDataManagerProperties.getTrajectoryFilePath()).thenReturn("");
        when(antaressDataManagerProperties.getLoadDirectory()).thenReturn("");

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
}