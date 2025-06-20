package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.WarningMessageRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.StudyServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DuplicationStudyImplTest {

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TrajectoryServiceImpl trajectoryService;

    @Mock
    private WarningMessageRepository  warningMessageRepository;

    @Mock
    private WarningMessageService warningMessageService;

    @Captor
    private ArgumentCaptor<List<String>> listCaptor;

    @InjectMocks
    private StudyServiceImpl studyService;



    @Test
    void duplicateStudy_withExistingAreaTrajectory_shouldDuplicateStudy() throws IOException {

        StudyDTO studyDTO = StudyDTO.builder()
                .name("test_duplication")
                .horizon("2031")
                .createdBy("user1")
                .project("project1")
                .build();

        ProjectEntity projectEntity = new ProjectEntity();
        projectEntity.setId(1);
        projectEntity.setName("project1");

        when(projectRepository.findByName("project1"))
                .thenReturn(Optional.of(projectEntity));

        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setProject(projectEntity);
        studyEntity.setHorizon("2030-2031");
        studyEntity.setStatus(StudyStatus.IN_PROGRESS);

        when(studyRepository.save(any(StudyEntity.class)))
                .thenReturn(studyEntity);

        TrajectoryEntity areaTrajectory = new TrajectoryEntity();
        areaTrajectory.setType("AREA");
        areaTrajectory.setId(1);

        List<TrajectoryEntity> trajectories = Collections.singletonList(areaTrajectory);

        when(trajectoryRepository.findMostRecentTrajectoriesByHorizon("2030-2031"))
                .thenReturn(trajectories);

        when(trajectoryService.linkTrajectoryToStudy(
                eq(1),
                eq(1),
                eq(TrajectoryType.AREA)
        )).thenReturn(areaTrajectory);

        when(warningMessageRepository.saveAll(any()))
                .thenReturn(Collections.emptyList());


        StudyDTO result = studyService.duplicateStudy(studyDTO);

        // Assert
        assertNotNull(result);
        assertEquals("2030-2031", result.getHorizon());
        verify(trajectoryRepository).findMostRecentTrajectoriesByHorizon("2030-2031");
        verify(projectRepository).findByName("project1");
        verify(studyRepository).save(any(StudyEntity.class));
        verify(trajectoryService).linkTrajectoryToStudy(eq(1), eq(1), eq(TrajectoryType.AREA));
        verify(warningMessageRepository).saveAll(Collections.emptySet());

    }
    @Test
    void duplicateStudy_withNoTrajectories_shouldThrowException() {

        StudyDTO studyDTO = StudyDTO.builder()
                .name("test_duplication")
                .horizon("2031")
                .createdBy("user1")
                .project("project1")
                .build();

        when(trajectoryRepository.findMostRecentTrajectoriesByHorizon("2030-2031"))
                .thenReturn(Collections.emptyList());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> studyService.duplicateStudy(studyDTO));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Duplicated study : No trajectory for horizon {0}. Cannot duplicate", exception.getMessage());

        verify(trajectoryRepository).findMostRecentTrajectoriesByHorizon("2030-2031");
        verifyNoMoreInteractions(
                trajectoryRepository,
                projectRepository,
                studyRepository,
                warningMessageRepository,
                warningMessageService,
                trajectoryService
        );
    }
    @Test
    void duplicateStudy_withoutAreaTrajectory_shouldThrowException() {

        StudyDTO studyDTO = StudyDTO.builder()
                .name("test_duplication")
                .horizon("2031")
                .createdBy("user1")
                .project("project1")
                .build();

        TrajectoryEntity linkTrajectory = new TrajectoryEntity();
        linkTrajectory.setType(TrajectoryType.LINK.name());
        linkTrajectory.setId(1);

        List<TrajectoryEntity> trajectories = Collections.singletonList(linkTrajectory);

        when(trajectoryRepository.findMostRecentTrajectoriesByHorizon("2030-2031"))
                .thenReturn(trajectories);


        BusinessException exception = assertThrows(BusinessException.class,
                () -> studyService.duplicateStudy(studyDTO));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Duplicated study : AREA trajectory does not exist for horizon {0}. No duplication done",
                exception.getMessage());

        verify(trajectoryRepository).findMostRecentTrajectoriesByHorizon("2030-2031");
        verifyNoMoreInteractions(
                projectRepository,
                studyRepository,
                warningMessageRepository,
                warningMessageService,
                trajectoryService
        );
    }
    @Test
    void duplicateStudy_withMissingTrajectories_shouldCreateWarnings() throws IOException {

        StudyDTO studyDTO = StudyDTO.builder()
                .name("test_duplication")
                .horizon("2031")
                .createdBy("user1")
                .project("project1")
                .build();

        TrajectoryEntity areaTrajectory = new TrajectoryEntity();
        areaTrajectory.setType(TrajectoryType.AREA.name());
        areaTrajectory.setId(1);

        List<TrajectoryEntity> trajectories = Collections.singletonList(areaTrajectory);

        when(trajectoryRepository.findMostRecentTrajectoriesByHorizon("2030-2031"))
                .thenReturn(trajectories);

        ProjectEntity projectEntity = new ProjectEntity();
        projectEntity.setId(1);
        projectEntity.setName("project1");

        when(projectRepository.findByName("project1"))
                .thenReturn(Optional.of(projectEntity));

        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setProject(projectEntity);
        studyEntity.setHorizon("2030-2031");
        studyEntity.setStatus(StudyStatus.IN_PROGRESS);

        when(studyRepository.save(any(StudyEntity.class)))
                .thenReturn(studyEntity);

        when(trajectoryService.linkTrajectoryToStudy(
                eq(1),
                eq(1),
                eq(TrajectoryType.AREA)
        )).thenReturn(areaTrajectory);

        Set<WarningMessageEntity> warningMessages = new HashSet<>();
        when(warningMessageRepository.saveAll(any()))
                .thenReturn(Collections.emptyList());


        StudyDTO result = studyService.duplicateStudy(studyDTO);


        assertNotNull(result);
        verify(trajectoryRepository).findMostRecentTrajectoriesByHorizon("2030-2031");
        verify(projectRepository).findByName("project1");
        verify(studyRepository).save(any(StudyEntity.class));
        verify(trajectoryService).linkTrajectoryToStudy(eq(1), eq(1), eq(TrajectoryType.AREA));
        verify(warningMessageService).addWarning(
                anySet(),
                listCaptor.capture(),
                eq(WarningCode.DUPLICATION_MISSING_TRAJECTORIES),
                eq(1),
                eq("user1"),
                eq(areaTrajectory)
        );

        List<String> actual = listCaptor.getValue();


        assertThat(actual).containsExactly("LINK, LOAD", "2031");

        verify(warningMessageRepository).saveAll(any());


    }


}



