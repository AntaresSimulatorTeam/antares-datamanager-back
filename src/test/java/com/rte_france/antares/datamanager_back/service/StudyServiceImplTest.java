package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.exception.BadRequestException;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.impl.StudyServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyServiceImplTest {

    @Mock
    private StudyRepository studyRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private StudyServiceImpl studyServiceImpl;


    @Test
    void findStudiesByCriteria_returnsFilteredStudiesWhenSearchIsNotNull() {
        Pageable pageable = PageRequest.of(0, 10);
        List<StudyEntity> studies = List.of(new StudyEntity());
        Page<StudyEntity> studyPage = new PageImpl<>(studies);
        String search = "test";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertEquals(studyPage, result);
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_returnsFilteredStudiesWhenIdProjectIsNotNull() {
        Pageable pageable = PageRequest.of(0, 10);
        List<StudyEntity> studies = List.of(new StudyEntity());
        Page<StudyEntity> studyPage = new PageImpl<>(studies);
        Integer idProject = 1;

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(null, idProject, pageable);

        assertEquals(studyPage, result);
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_returnsFilteredStudiesWhenSearchAndIdProjectAreNotNull() {
        Pageable pageable = PageRequest.of(0, 10);
        List<StudyEntity> studies = List.of(new StudyEntity());
        Page<StudyEntity> studyPage = new PageImpl<>(studies);
        String search = "test";
        Integer idProject = 1;

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, idProject, pageable);

        assertEquals(studyPage, result);
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
void searchKeywordsByPartialNameReturnsMatchingKeywords() {
    when(studyRepository.findKeywordsByPartialName("key")).thenReturn(List.of("keyword1", "keyword2"));

    List<String> keywords = studyServiceImpl.searchKeywordsByPartialName("key");

    assertThat(keywords).isNotNull();
    assertThat(keywords).isNotEmpty();
    assertThat(keywords).contains("keyword1", "keyword2");
    verify(studyRepository, times(1)).findKeywordsByPartialName("key");
}

@Test
void searchKeywordsByPartialNameReturnsEmptyListWhenNoMatches() {
    when(studyRepository.findKeywordsByPartialName("nonExistent")).thenReturn(List.of());

    List<String> keywords = studyServiceImpl.searchKeywordsByPartialName("nonExistent");

    assertThat(keywords).isNotNull();
    assertThat(keywords).isEmpty();
    verify(studyRepository, times(1)).findKeywordsByPartialName("nonExistent");
}

@Test
void searchKeywordsByPartialNameHandlesNullInput() {
    when(studyRepository.findKeywordsByPartialName(null)).thenReturn(List.of());

    List<String> keywords = studyServiceImpl.searchKeywordsByPartialName(null);

    assertThat(keywords).isNotNull();
    assertThat(keywords).isEmpty();
    verify(studyRepository, times(1)).findKeywordsByPartialName(null);
}

    @Test
    void createStudyCreatesNewProjectWhenProjectNotExists() {
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").project("New Project").build();
        ProjectEntity newProject = new ProjectEntity();
        newProject.setId(1);
        newProject.setName("New Project");
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setName("Study 1");
        studyEntity.setCreatedBy("User 1");
        studyEntity.setProject(newProject);

        when(projectRepository.findByName("New Project")).thenReturn(Optional.empty());
        when(projectRepository.save(any(ProjectEntity.class))).thenReturn(newProject);
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(studyEntity);

        StudyDTO result = studyServiceImpl.createStudy(studyDTO);

        assertEquals(1, result.getId());
        assertEquals("Study 1", result.getName());
        assertEquals("User 1", result.getCreatedBy());
        verify(projectRepository, times(1)).findByName("New Project");
        verify(projectRepository, times(1)).save(any(ProjectEntity.class));
        verify(studyRepository, times(1)).save(any(StudyEntity.class));
    }

    @Test
    void createStudyUsesExistingProjectWhenProjectExists() {
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").project("Existing Project").build();
        ProjectEntity existingProject = new ProjectEntity();
        existingProject.setId(1);
        existingProject.setName("Existing Project");
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setName("Study 1");
        studyEntity.setCreatedBy("User 1");
        studyEntity.setProject(existingProject);

        when(projectRepository.findByName("Existing Project")).thenReturn(Optional.of(existingProject));
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(studyEntity);

        StudyDTO result = studyServiceImpl.createStudy(studyDTO);

        assertEquals(1, result.getId());
        assertEquals("Study 1", result.getName());
        assertEquals("User 1", result.getCreatedBy());
        verify(projectRepository, times(1)).findByName("Existing Project");
        verify(studyRepository, times(1)).save(any(StudyEntity.class));
    }

    @Test
    void createStudyThrowsBadRequestWhenNoProjectNameProvided() {
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").build();

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            studyServiceImpl.createStudy(studyDTO);
        });

        assertEquals("Project name must be provided.", exception.getMessage());
        verify(projectRepository, never()).findByName(anyString());
        verify(projectRepository, never()).save(any(ProjectEntity.class));
        verify(studyRepository, never()).save(any(StudyEntity.class));
    }

}
