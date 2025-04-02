package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.exception.BadRequestException;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyStatus;
import com.rte_france.antares.datamanager_back.service.impl.StudyServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
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

import java.time.Year;
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

    @Mock
    private UserService userService;

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

        assertThat(keywords).isNotNull().isNotEmpty().contains("keyword1", "keyword2");
        verify(studyRepository, times(1)).findKeywordsByPartialName("key");
    }

    @Test
    void searchKeywordsByPartialNameReturnsEmptyListWhenNoMatches() {
        when(studyRepository.findKeywordsByPartialName("nonExistent")).thenReturn(List.of());

        List<String> keywords = studyServiceImpl.searchKeywordsByPartialName("nonExistent");

        assertThat(keywords).isNotNull().isEmpty();
        verify(studyRepository, times(1)).findKeywordsByPartialName("nonExistent");
    }

    @Test
    void searchKeywordsByPartialNameHandlesNullInput() {
        when(studyRepository.findKeywordsByPartialName(null)).thenReturn(List.of());

        List<String> keywords = studyServiceImpl.searchKeywordsByPartialName(null);

        assertThat(keywords).isNotNull().isEmpty();
        verify(studyRepository, times(1)).findKeywordsByPartialName(null);
    }

    @Test
    void createStudyThrowsBadRequestWhenProjectWithSameNameExist() {
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").project("Existing Project").horizon("2030").build();

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            studyServiceImpl.createStudy(studyDTO);
        });

        assertEquals("Project not found with name: Existing Project", exception.getMessage());
        verify(studyRepository, never()).save(any(StudyEntity.class));
    }

    @Test
    void createStudyThrowsBadRequestWhenStudyWithSameNameExists() {
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").project("Existing Project").horizon("2050").build();
        when(studyRepository.existsByNameAndProjectName("Study 1-2051", "Existing Project")).thenReturn(true);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            studyServiceImpl.createStudy(studyDTO);
        });

        assertEquals("A study with the same name already exists for the given project.", exception.getMessage());
        verify(studyRepository, never()).save(any(StudyEntity.class));
    }

    @Test
    void createStudyUsesExistingProjectWhenProjectExists() {
        String currentYear = String.valueOf(Year.now().getValue());
        String nextYear = String.valueOf(Year.now().getValue() + 1);
        String horizon = currentYear + "-" + nextYear;

        String studyName = "Study 1-" + currentYear + "-" + nextYear + "ref";
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").project("Existing Project").horizon(currentYear).build();
        ProjectEntity existingProject = new ProjectEntity();
        existingProject.setId(1);
        existingProject.setName("Existing Project");
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setName(studyName);
        studyEntity.setCreatedBy("User 1");
        studyEntity.setProject(existingProject);
        studyEntity.setHorizon(horizon);
        studyEntity.setStatus(StudyStatus.IN_PROGRESS);
        when(projectRepository.findByName("Existing Project")).thenReturn(Optional.of(existingProject));
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(studyEntity);

        StudyDTO result = studyServiceImpl.createStudy(studyDTO);

        assertEquals(1, result.getId());
        assertEquals(studyName, result.getName());
        assertEquals("User 1", result.getCreatedBy());
        assertEquals(horizon, result.getHorizon());
        verify(projectRepository, times(1)).findByName("Existing Project");
        verify(studyRepository, times(1)).save(any(StudyEntity.class));
    }

    @Test
    void createStudyThrowsBadRequestWhenNoProjectNameProvided() {
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").horizon("2021").build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            studyServiceImpl.createStudy(studyDTO);
        });

        assertEquals("Project name must be provided.", exception.getMessage());
        verify(projectRepository, never()).findByName(anyString());
        verify(projectRepository, never()).save(any(ProjectEntity.class));
        verify(studyRepository, never()).save(any(StudyEntity.class));
    }

    @Test
    void deleteStudyByIdDeletesStudyWhenExists() {
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);

        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));

        studyServiceImpl.deleteStudyById(1);

        verify(studyRepository, times(1)).delete(studyEntity);
    }

    @Test
    void deleteStudyByIdThrowsBadRequestExceptionWhenStudyNotFound() {
        when(studyRepository.findById(1)).thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            studyServiceImpl.deleteStudyById(1);
        });

        assertEquals("Study with id 1 not found.", exception.getMessage());
        verify(studyRepository, never()).delete(any(StudyEntity.class));
    }

    @Test
    void updateStudyStatusAsGenerated_updatesStatusWhenStudyExists() {
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setStatus(StudyStatus.IN_PROGRESS);

        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));

        studyServiceImpl.updateStudyStatusAsGenerated(1);

        assertEquals(StudyStatus.GENERATED, studyEntity.getStatus());
        verify(studyRepository).save(studyEntity);
    }

    @Test
    void updateStudyStatusAsGenerated_throwsExceptionWhenStudyNotFound() {
        when(studyRepository.findById(1)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            studyServiceImpl.updateStudyStatusAsGenerated(1);
        });

        assertEquals("Study not found with ID: 1", exception.getMessage());
        verify(studyRepository, never()).save(any(StudyEntity.class));
    }

    @Test
    void findStudyByIdFindStudyWhenExists() {
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);

        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));

        studyServiceImpl.findStudyById(1);

        verify(studyRepository, times(1)).findById(studyEntity.getId());
    }

    @Test
    void findStudyByIdReturnNullWhenStudyNotFound() {
        when(studyRepository.findById(1)).thenReturn(null);
    }
}
