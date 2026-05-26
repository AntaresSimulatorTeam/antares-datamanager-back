package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.WarningService;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.study.impl.StudyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuplicationStudyImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TrajectoryServiceImpl trajectoryService;


    @Mock
    private WarningRepository warningRepository;

    @Mock
    private WarningService warningService;

    @Captor
    private ArgumentCaptor<List<String>> listCaptor;

    @InjectMocks
    private StudyServiceImpl studyService;

    ProjectEntity projectEntity = new ProjectEntity();
    StudyEntity studyEntity = new StudyEntity();

    @BeforeEach
    void setup() {

        projectEntity.setId(1);
        projectEntity.setName("project1");

        studyEntity.setId(1);
        studyEntity.setProject(projectEntity);
        studyEntity.setHorizon("2030-2031");
        studyEntity.setStatus(StudyStatus.IN_PROGRESS);


    }

    @Test
    void duplicateStudy_withExistingAreaTrajectory_shouldDuplicateStudy() throws IOException {

        StudyDTO studyDTO = StudyDTO.builder()
                .name("test_duplication")
                .horizon("2031")
                .createdBy("user1")
                .project("project1")
                .id(15)
                .build();


        when(projectRepository.findByName("project1"))
                .thenReturn(Optional.of(projectEntity));

        when(studyRepository.save(any(StudyEntity.class)))
                .thenReturn(studyEntity);

        TrajectoryEntity areaTrajectory = new TrajectoryEntity();
        areaTrajectory.setType("AREA");
        areaTrajectory.setId(1);
        areaTrajectory.setFileName("BP1");
        areaTrajectory.setWarningMessages(new HashSet<>());

        Set<TrajectoryEntity> trajectories = Set.of(areaTrajectory);
        studyEntity.setTrajectories(trajectories);

        when(trajectoryService.linkTrajectoryToStudy(1, 1, TrajectoryType.AREA)).thenReturn(areaTrajectory);
        // Mock checkTrajectoryCoherence to avoid IOException
        doAnswer(invocation -> null).when(trajectoryService).checkTrajectoryCoherence(anyInt(), any(), any(TrajectoryEntity.class), anyString());
        // Mock trajectoryRepository
        when(trajectoryRepository.findAllByIdWithWarnings(List.of(1))).thenReturn(trajectories);

        when(studyRepository.findById(15)).thenReturn(Optional.of(studyEntity));


        StudyDTO result = studyService.duplicateStudy(studyDTO);

        // Assert
        assertNotNull(result);
        assertEquals("2030-2031", result.getHorizon());
        verify(projectRepository).findByName("project1");
        verify(studyRepository).save(any(StudyEntity.class));
        verify(trajectoryService).linkTrajectoryToStudy(1,1, TrajectoryType.AREA);
        // Verify that checkTrajectoryCoherence was called
        verify(trajectoryService).checkTrajectoryCoherence(eq(1), any(), eq(areaTrajectory), eq("user1"));


    }

    @Test
    void duplicateStudy_withoutAreaTrajectory_shouldThrowException() throws IOException {

        StudyDTO studyDTO = StudyDTO.builder()
                .name("test_duplication")
                .horizon("2036")
                .createdBy("user1")
                .project("project1")
                .id(1)
                .build();
        TrajectoryEntity areaTrajectory = new TrajectoryEntity();
        areaTrajectory.setType("AREA");
        areaTrajectory.setId(1);
        areaTrajectory.setHorizon("2026-2027");
        areaTrajectory.setFileName("BP1");
        areaTrajectory.setWarningMessages(new HashSet<>());

        studyEntity.setTrajectories(Set.of(areaTrajectory));

        TrajectoryEntity linkTrajectory = new TrajectoryEntity();
        linkTrajectory.setType(TrajectoryType.LINK.name());
        linkTrajectory.setId(1);

        when(studyRepository.findById(anyInt())).thenReturn(Optional.of(studyEntity));


        BusinessException exception = assertThrows(BusinessException.class,
                () -> studyService.duplicateStudy(studyDTO));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("AREA trajectory {0} does not exist for horizon {1}",
                exception.getMessage());

        verifyNoMoreInteractions(
                projectRepository,
                warningRepository,
                warningService
        );
    }

    @Test
    void duplicateStudy_withMissingTrajectories_shouldCreateWarnings() throws IOException {

        StudyDTO studyDTO = StudyDTO.builder()
                .name("test_duplication")
                .horizon("2031")
                .createdBy("user1")
                .project("project1")
                .id(1)
                .build();

        TrajectoryEntity areaTrajectory = new TrajectoryEntity();
        areaTrajectory.setType(TrajectoryType.AREA.name());
        areaTrajectory.setId(1);
        areaTrajectory.setFileName("BP1");
        areaTrajectory.setWarningMessages(new HashSet<>());

        studyEntity.setTrajectories(Set.of(areaTrajectory));
        when(studyRepository.findById(anyInt())).thenReturn(Optional.of(studyEntity));


        when(projectRepository.findByName("project1"))
                .thenReturn(Optional.of(projectEntity));

        when(studyRepository.save(any(StudyEntity.class)))
                .thenReturn(studyEntity);

        when(trajectoryService.linkTrajectoryToStudy(1, 1, TrajectoryType.AREA)).thenReturn(areaTrajectory);
        // Mock checkTrajectoryCoherence to avoid IOException
        doAnswer(invocation -> null).when(trajectoryService).checkTrajectoryCoherence(anyInt(), any(), any(TrajectoryEntity.class), anyString());
        // Mock trajectoryRepository - for same horizon, return the area only
         when(trajectoryRepository.findAllByIdWithWarnings(List.of(1))).thenReturn(Set.of(areaTrajectory));


         StudyDTO result = studyService.duplicateStudy(studyDTO);


         assertNotNull(result);
         verify(projectRepository).findByName("project1");
         verify(studyRepository).save(any(StudyEntity.class));
         verify(trajectoryService).linkTrajectoryToStudy(1, 1, TrajectoryType.AREA);
         // Verify that checkTrajectoryCoherence was called for the area trajectory
         verify(trajectoryService).checkTrajectoryCoherence(eq(1), any(), eq(areaTrajectory), eq("user1"));



    }

    @Test
    void duplicateStudy_checkTrajectoryCoherenceHandlesMultipleTypes() throws IOException {

        StudyDTO studyDTO = StudyDTO.builder()
                .name("test_duplication")
                .horizon("2031")
                .createdBy("user1")
                .project("project1")
                .id(1)
                .build();

        TrajectoryEntity areaTrajectory = new TrajectoryEntity();
        areaTrajectory.setType(TrajectoryType.AREA.name());
        areaTrajectory.setId(1);
        areaTrajectory.setFileName("BP1");
        areaTrajectory.setWarningMessages(new HashSet<>());

        TrajectoryEntity loadTrajectory = new TrajectoryEntity();
        loadTrajectory.setType(TrajectoryType.LOAD.name());
        loadTrajectory.setId(2);
        loadTrajectory.setFileName("LOAD1");
        loadTrajectory.setWarningMessages(new HashSet<>());

        Set<TrajectoryEntity> trajectories = Set.of(areaTrajectory, loadTrajectory);
        studyEntity.setTrajectories(trajectories);
        when(studyRepository.findById(anyInt())).thenReturn(Optional.of(studyEntity));

        when(projectRepository.findByName("project1"))
                .thenReturn(Optional.of(projectEntity));

        when(studyRepository.save(any(StudyEntity.class)))
                .thenReturn(studyEntity);

        when(trajectoryService.linkTrajectoryToStudy(anyInt(), eq(1), any(TrajectoryType.class)))
                .thenReturn(areaTrajectory);
        
        // Mock checkTrajectoryCoherence to avoid IOException
        doAnswer(invocation -> null).when(trajectoryService).checkTrajectoryCoherence(anyInt(), any(), any(TrajectoryEntity.class), anyString());
        // Mock trajectoryRepository - for same horizon, return all trajectories
        when(trajectoryRepository.findAllByIdWithWarnings(any())).thenReturn(trajectories);


        StudyDTO result = studyService.duplicateStudy(studyDTO);

        assertNotNull(result);
        
        // Verify that checkTrajectoryCoherence was called for both trajectories
        verify(trajectoryService, times(2)).checkTrajectoryCoherence(anyInt(), any(), any(TrajectoryEntity.class), anyString());
        verify(trajectoryService).checkTrajectoryCoherence(eq(1), any(), eq(areaTrajectory), eq("user1"));
        verify(trajectoryService).checkTrajectoryCoherence(eq(1), any(), eq(loadTrajectory), eq("user1"));
    }

    @Test
    void duplicateStudy_checkTrajectoryCoherenceHandlesIOException() throws IOException {

        StudyDTO studyDTO = StudyDTO.builder()
                .name("test_duplication")
                .horizon("2031")
                .createdBy("user1")
                .project("project1")
                .id(1)
                .build();

        TrajectoryEntity areaTrajectory = new TrajectoryEntity();
        areaTrajectory.setType(TrajectoryType.AREA.name());
        areaTrajectory.setId(1);
        areaTrajectory.setWarningMessages(new HashSet<>());

        StudyEntity studyEntityWithArea = new StudyEntity();
        studyEntityWithArea.setId(1);
        studyEntityWithArea.setProject(projectEntity);
        studyEntityWithArea.setHorizon("2030-2031");
        studyEntityWithArea.setStatus(StudyStatus.IN_PROGRESS);
        studyEntityWithArea.setTrajectories(Set.of(areaTrajectory));

        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntityWithArea));

        // Should throw an exception when accessing without mocking the checkTrajectoryCoherence
        assertThrows(Exception.class, () -> studyService.duplicateStudy(studyDTO));
    }


}



