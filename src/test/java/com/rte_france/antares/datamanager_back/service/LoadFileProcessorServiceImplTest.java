package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.WarningService;
import com.rte_france.antares.datamanager_back.service.load.impl.LoadFileProcessorServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoadFileProcessorServiceImplTest {
    @InjectMocks
    private LoadFileProcessorServiceImpl loadFileProcessorService;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private LoadRepository loadRepository;

    @Mock
    private AreaRepository areaRepository;


    @Mock
    private WarningService warningService;


    @TempDir
    private Path tempDir;

    private String horizon;
    private Integer studyId;
    private String userNni;
    private TrajectoryEntity trajectory;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        horizon = "2023-2024";
        studyId = 1;
        userNni = "testUser";
        trajectory = TrajectoryEntity.builder()
                .fileName("testTrajectory")
                .build();

    }

    @Test
    void shouldThrowBusinessExceptionWhenAllFilesAreMissing() throws IOException {
        // Given
        List<String> studyAreas = List.of("AREA1", "AREA2");
        List<AreaEntity> areaEntities = studyAreas.stream()
                .map(name -> AreaEntity.builder().name(name).build())
                .toList();

        when(areaRepository.findAllByStudyId(studyId)).thenReturn(areaEntities);
        when(trajectoryRepository.findByTypeAndStudyId(eq("LOAD"), eq(studyId)))
                .thenReturn(List.of());

        Path trajectoryPath = tempDir.resolve("testTrajectory");
        Files.createDirectory(trajectoryPath);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () ->
                loadFileProcessorService.checkForMissingLoadFiles(trajectoryPath, horizon, studyId, userNni, trajectory));

        assertEquals("Missing file(s) for area(s) {0} in LOAD Other areas trajectory {1}\n" +
                "Please re import trajectory {1} to complete area(s)", exception.getMessage());
        assertEquals(List.of("AREA1, AREA2", "testTrajectory"), exception.getErrorMessageArguments());
        verify(warningService, never()).addWarning(any(), any(), any(), any(), any(), any());

        verify(warningService, never()).addWarning(any(), any(), any(), any(), any(), any());
    }


    @Test
    void shouldAddWarningWhenSomeFilesAreMissing() throws IOException {
        // Given
        List<String> studyAreas = List.of("AREA1", "AREA2", "AREA3" );
        List<AreaEntity> areaEntities = studyAreas.stream()
                .map(name -> AreaEntity.builder().name(name).build())
                .toList();


        when(areaRepository.findAllByStudyId(studyId)).thenReturn(areaEntities);
        when(trajectoryRepository.findByTypeAndStudyId(eq("LOAD"), eq(studyId)))
                .thenReturn(List.of());

        Path trajectoryPath = tempDir.resolve("testTrajectory");
        Files.createDirectory(trajectoryPath);

        Path loadArea1File = trajectoryPath.resolve("load_area1_" + horizon + ".txt");
        Files.writeString(loadArea1File, "test content");

        //When
        Set<WarningMessageEntity> result = loadFileProcessorService.checkForMissingLoadFiles(
                trajectoryPath, horizon, studyId, userNni, trajectory);

        //Then
        verify(warningService).addWarning(
                eq(result),
                argThat(warnings -> warnings.size() == 2 &&
                        warnings.getFirst().equals("AREA2, AREA3") &&
                        warnings.get(1).equals("testTrajectory")),
                eq(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS),
                eq(studyId),
                eq(userNni),
                eq(trajectory)
        );


        assertTrue(Files.exists(loadArea1File));
        assertTrue(Files.isRegularFile(loadArea1File));
        assertEquals("test content", Files.readString(loadArea1File));
    }

    @Test
    void shouldReturnEmptyWarningSetWhenAllFilesExist() throws IOException {
        // Given
        List<String> studyAreas = List.of("AREA1", "AREA2");
        List<AreaEntity> areaEntities = studyAreas.stream()
                .map(name -> AreaEntity.builder().name(name).build())
                .toList();

        when(areaRepository.findAllByStudyId(studyId)).thenReturn(areaEntities);
        when(trajectoryRepository.findByTypeAndStudyId(eq("LOAD"), eq(studyId)))
                .thenReturn(List.of());

        Path trajectoryPath = tempDir.resolve("testTrajectory");
        Files.createDirectory(trajectoryPath);


        for (String area : studyAreas) {
            Path areaFile = trajectoryPath.resolve("load_" + area.toLowerCase() + "_" + horizon + ".txt");
            Files.writeString(areaFile, "test content");
        }

        // When
        Set<WarningMessageEntity> result = loadFileProcessorService.checkForMissingLoadFiles(
                trajectoryPath, horizon, studyId, userNni, trajectory);

        // Then
        assertTrue(result.isEmpty());
        verify(warningService, never()).getMessage(anyString(), any());
        verify(warningService, never()).addWarning(any(), any(), any(), any(), any(), any());
    }

    @Test
    void checkForMissingLoadByAreaFromDb_usesOnlyThisTrajectoryLoadEntities() {
        // Given
        var studyAreas = List.of("BE", "DE");
        var areaEntities = studyAreas.stream()
                .map(n -> AreaEntity.builder().name(n).build())
                .toList();
        var beLoad = LoadEntity.builder().area("be").fileName("load_BE_" + horizon + ".txt").build();

        // When
        when(areaRepository.findAllByStudyId(studyId)).thenReturn(areaEntities);
        when(trajectoryRepository.findByTypeAndStudyId("LOAD", studyId))
                .thenReturn(List.of());

        trajectory.setLoadEntities(new HashSet<>(List.of(beLoad)));

        Set<WarningMessageEntity> result = loadFileProcessorService.checkForMissingLoadByAreaFromDb(
                horizon, studyId, userNni, trajectory
        );

        // Then
        verify(warningService).addWarning(
                eq(result),
                argThat(args -> args.size() == 2 &&
                        args.get(0).equals("DE") &&
                        args.get(1).equals(trajectory.getFileName())),
                eq(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS),
                eq(studyId),
                eq(userNni),
                eq(trajectory)
        );

        verify(loadRepository, never()).findByFileNameAndTrajectoryFileName(anyString(), anyString());
    }

    @Test
    void checkForMissingLoadByAreaFromDb_fallbacksToDbWhenNoLoadEntities() {
        // Given
        var studyAreas = List.of("BE", "DE");
        var areaEntities = studyAreas.stream()
                .map(n -> AreaEntity.builder().name(n).build())
                .toList();

        // When
        when(areaRepository.findAllByStudyId(studyId)).thenReturn(areaEntities);
        when(trajectoryRepository.findByTypeAndStudyId("LOAD", studyId))
                .thenReturn(List.of());

        trajectory.setLoadEntities(Collections.emptySet());
        trajectory.setFileName("OTHERS");

        when(loadRepository.findByFileNameAndTrajectoryFileName(
                "load_BE_" + horizon + ".txt", "OTHERS")).thenReturn(Optional.of(LoadEntity.builder().build()));
        when(loadRepository.findByFileNameAndTrajectoryFileName(
                "load_DE_" + horizon + ".txt", "OTHERS")).thenReturn(Optional.empty());

        Set<WarningMessageEntity> result = loadFileProcessorService.checkForMissingLoadByAreaFromDb(
                horizon, studyId, userNni, trajectory
        );

        // Then
        verify(warningService).addWarning(
                eq(result),
                argThat(args -> args.size() == 2 &&
                        args.get(0).equals("DE") &&
                        args.get(1).equals("OTHERS")),
                eq(WarningCode.LOAD_MISSING_TRAJECTORY_FOR_AREAS),
                eq(studyId),
                eq(userNni),
                eq(trajectory)
        );

        verify(loadRepository).findByFileNameAndTrajectoryFileName("load_BE_" + horizon + ".txt", "OTHERS");
        verify(loadRepository).findByFileNameAndTrajectoryFileName("load_DE_" + horizon + ".txt", "OTHERS");
    }

}