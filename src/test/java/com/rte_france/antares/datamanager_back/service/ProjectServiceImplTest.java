package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private StudyRepository studyRepository;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Test
    void createProject_returnsProjectEntity_whenProjectDoesNotExistAndAllStudiesExist() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");
        projectInputDto.setStudyIds(List.of(1, 2));

        when(projectRepository.findByName(any(String.class))).thenReturn(Optional.empty());
        when(studyRepository.findAllById(any(List.class))).thenReturn(List.of(new StudyEntity(), new StudyEntity()));
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        ProjectEntity projectEntity = projectService.createProject(projectInputDto);

        assertEquals(projectInputDto.getName(), projectEntity.getName());
        assertEquals(projectInputDto.getStudyIds().size(), projectEntity.getStudies().size());
    }

    @Test
    void createProject_throwsException_whenProjectExists() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");
        projectInputDto.setStudyIds(List.of(1, 2));

        when(projectRepository.findByName(any(String.class))).thenReturn(Optional.of(new ProjectEntity()));

        assertThrows(IllegalArgumentException.class, () -> projectService.createProject(projectInputDto));
    }

    @Test
    void createProject_throwsException_whenSomeStudiesDoNotExist() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");
        projectInputDto.setStudyIds(List.of(1, 2));

        when(projectRepository.findByName(any(String.class))).thenReturn(Optional.empty());
        when(studyRepository.findAllById(any(List.class))).thenReturn(List.of(new StudyEntity()));

        assertThrows(IllegalArgumentException.class, () -> projectService.createProject(projectInputDto));
    }

    @Test
    void createProject_throwsException_whenNoStudiesProvided() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");
        projectInputDto.setStudyIds(List.of());

        assertThrows(IllegalArgumentException.class, () -> projectService.createProject(projectInputDto));
    }

    @Test
    void createProject_createsProject_whenOneStudyProvided() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");
        projectInputDto.setStudyIds(List.of(1));

        when(projectRepository.findByName(any(String.class))).thenReturn(Optional.empty());
        when(studyRepository.findAllById(any(List.class))).thenReturn(List.of(new StudyEntity()));
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        ProjectEntity projectEntity = projectService.createProject(projectInputDto);

        assertEquals(projectInputDto.getName(), projectEntity.getName());
        assertEquals(projectInputDto.getStudyIds().size(), projectEntity.getStudies().size());
    }
}
