package com.rte_france.antares.datamanager_back.service;
import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyStatus;
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
import org.springframework.http.HttpStatus;

import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyServiceImplTest {

    public static final String EXISTING_PROJECT = "Existing Project";
    @Mock
    private StudyRepository studyRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private StudyGeneratorService studyGeneratorService;

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
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").project(EXISTING_PROJECT).horizon("2030").build();

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            studyServiceImpl.createStudy(studyDTO);
        });

        assertEquals("Project not found with name: {0}", exception.getMessage());
        assertEquals("Project not found with name: {0}", exception.getMessage());
        verify(studyRepository, never()).save(any(StudyEntity.class));
    }

    @Test
    void createStudyThrowsBadRequestWhenStudyWithSameNameExists() {
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").project(EXISTING_PROJECT).horizon("2050").build();
        when(studyRepository.existsByNameAndProjectName("Study 1_2050", EXISTING_PROJECT)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
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
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").project(EXISTING_PROJECT).horizon(currentYear).build();
        ProjectEntity existingProject = new ProjectEntity();
        existingProject.setId(1);
        existingProject.setName(EXISTING_PROJECT);
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setName(studyName);
        studyEntity.setCreatedBy("User 1");
        studyEntity.setProject(existingProject);
        studyEntity.setHorizon(horizon);
        studyEntity.setStatus(StudyStatus.IN_PROGRESS);
        when(projectRepository.findByName(EXISTING_PROJECT)).thenReturn(Optional.of(existingProject));
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(studyEntity);

        StudyDTO result = studyServiceImpl.createStudy(studyDTO);

        assertEquals(1, result.getId());
        assertEquals(studyName, result.getName());
        assertEquals("User 1", result.getCreatedBy());
        assertEquals(horizon, result.getHorizon());
        verify(projectRepository, times(1)).findByName(EXISTING_PROJECT);
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
    void deleteStudyByIdThrowsBusinessExceptionWhenStudyNotFound() {
        when(studyRepository.findById(1)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            studyServiceImpl.deleteStudyById(1);
        });

        assertEquals("Study with id {0} not found.", exception.getMessage());
        assertEquals(Collections.singletonList("1"), exception.getErrorMessageArguments());
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

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            studyServiceImpl.updateStudyStatusAsGenerated(1);
        });

        assertEquals("Study not found with ID: {0}", exception.getMessage());
        assertEquals(Collections.singletonList("1"), exception.getErrorMessageArguments());

        verify(studyRepository, never()).save(any(StudyEntity.class));
    }

    @Test
    void findStudyByIdFindStudyWhenExists() {
        ProjectEntity projectEntity = new ProjectEntity();
        projectEntity.setId(1);
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setProject(projectEntity);
        studyEntity.setStatus(StudyStatus.IN_PROGRESS);

        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));

        studyServiceImpl.findStudyById(1);

        verify(studyRepository, times(1)).findById(studyEntity.getId());
    }

    @Test
    void findStudyByIdReturnNullWhenStudyNotFound() {
        when(studyRepository.findById(1)).thenReturn(Optional.empty());
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            studyServiceImpl.findStudyById(1);
        });

        assertEquals("Study with id {0} not found.", exception.getMessage());
        assertEquals(Collections.singletonList("1"), exception.getErrorMessageArguments());
    }



    @Test
    void generateStudyExecutesAllStepsSuccessfully() throws TechnicalException {
        StudyEntity studyEntity = new StudyEntity();
        studyEntity.setId(1);
        studyEntity.setStatus(StudyStatus.IN_PROGRESS);

        doNothing().when(studyGeneratorService).buildJsonForStudyGeneration(1);
        doNothing().when(studyGeneratorService).callGenerateStudyService(1);
        when(studyRepository.findById(1)).thenReturn(Optional.of(studyEntity));
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(studyEntity);

        studyServiceImpl.generateStudy(1);

        verify(studyGeneratorService, times(1)).buildJsonForStudyGeneration(1);
        verify(studyGeneratorService, times(1)).callGenerateStudyService(1);
        verify(studyRepository, times(1)).save(any(StudyEntity.class));
    }

    @Test
    void generateStudyThrowsTechnicalExceptionWhenJsonGenerationFails() throws TechnicalException {
        doThrow( TechnicalException.builder().message("Error during JSON generation").build())
                .when(studyGeneratorService).buildJsonForStudyGeneration(1);

        TechnicalException exception = assertThrows(TechnicalException.class, () -> {
            studyServiceImpl.generateStudy(1);
        });

        assertEquals("Error during JSON generation", exception.getMessage());
        verify(studyGeneratorService, times(1)).buildJsonForStudyGeneration(1);
        verify(studyGeneratorService, never()).callGenerateStudyService(1);
        verify(studyRepository, never()).save(any(StudyEntity.class));
    }

    @Test
    void generateStudyThrowsTechnicalExceptionWhenServiceCallFails() throws TechnicalException {
        doNothing().when(studyGeneratorService).buildJsonForStudyGeneration(1);
        doThrow(TechnicalException.builder().message("Error during service call").build())
                .when(studyGeneratorService).callGenerateStudyService(1);

        TechnicalException exception = assertThrows(TechnicalException.class, () -> {
            studyServiceImpl.generateStudy(1);
        });

        assertEquals("Error during service call", exception.getMessage());
        verify(studyGeneratorService, times(1)).buildJsonForStudyGeneration(1);
        verify(studyGeneratorService, times(1)).callGenerateStudyService(1);
        verify(studyRepository, never()).save(any(StudyEntity.class));
    }

    @Test
    void validateHorizon_shouldThrowException_whenYearAboveUpperBound() {

        StudyDTO studyDTO = new StudyDTO();
        studyDTO.setName("HorizonUpperBound");
        studyDTO.setHorizon("10000");
        studyDTO.setProject("project");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> studyServiceImpl.createStudy(studyDTO)
        );

        assertAll(
                () -> assertEquals("Horizon must be between 2000 and 9999", exception.getMessage()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus())
        );
    }

    @Test
    void validateHorizon_shouldThrowException_whenNotANumber() {

        StudyDTO studyDTO = new StudyDTO();
        studyDTO.setName("HorizonString");
        studyDTO.setHorizon("abc");
        studyDTO.setProject("project");


        assertThrows(
                NumberFormatException.class,
                () -> studyServiceImpl.createStudy(studyDTO)
        );

    }

    @Test
    void updateStudy_updatesProjectHorizonAndTags_whenInProgress() {
        var studyId = 42;

        var oldProject = ProjectEntity.builder()
                .id(10)
                .name("Old")
                .build();

        var study = StudyEntity.builder()
                .id(studyId)
                .name("MyStudy_2030")
                .project(oldProject)
                .status(StudyStatus.IN_PROGRESS)
                .horizon("2030")
                .tags(List.of("a"))
                .build();

        var dto = StudyDTO.builder()
                .project("NewProject")
                .horizon("2032")
                .tags(List.of("x", "y"))
                .build();

        var newProject = ProjectEntity.builder()
                .id(11)
                .name("NewProject")
                .build();

        when(studyRepository.findById(studyId)).thenReturn(Optional.of(study));
        when(projectRepository.findByName("NewProject")).thenReturn(Optional.of(newProject));
        when(studyRepository.existsByNameAndProjectName("MyStudy_2030", "NewProject")).thenReturn(false);
        when(studyRepository.save(any(StudyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = studyServiceImpl.updateStudy(studyId, dto);

        assertEquals("2031-2032", study.getHorizon());
        assertEquals(newProject, study.getProject());
        assertEquals(List.of("x", "y"), study.getTags());
        assertEquals("2031-2032", result.getHorizon());

        verify(studyRepository).save(study);
    }

    @Test
    void updateStudy_throwsNotFound_whenStudyMissing() {
        when(studyRepository.findById(999)).thenReturn(Optional.empty());

        var dto = StudyDTO.builder().build();
        var ex = assertThrows(BusinessException.class,
                () -> studyServiceImpl.updateStudy(999, dto));

        assertEquals("Study with id {0} not found.", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void updateStudy_throwsBadRequest_whenStatusGenerated() {
        var study = StudyEntity.builder()
                .id(1)
                .status(StudyStatus.GENERATED)
                .build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));

        var dto = StudyDTO.builder().project("P").build();
        var ex = assertThrows(BusinessException.class,
                () -> studyServiceImpl.updateStudy(1, dto));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void updateStudy_throwsConflict_whenSameNameExistsInTargetProject() {
        var study = StudyEntity.builder()
                .id(1)
                .name("MyStudy_2030")
                .project(ProjectEntity.builder().id(7).name("Old").build())
                .status(StudyStatus.IN_PROGRESS)
                .build();

        var newProject = ProjectEntity.builder().id(8).name("New").build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(projectRepository.findByName("New")).thenReturn(Optional.of(newProject));
        when(studyRepository.existsByNameAndProjectName("MyStudy_2030", "New")).thenReturn(true);

        var dto = StudyDTO.builder().project("New").build();
        var ex = assertThrows(BusinessException.class,
                () -> studyServiceImpl.updateStudy(1, dto));

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    void updateStudy_throwsConflict_whenStudyAlreadyExistForTheGivenProject() {
        var currentProject = ProjectEntity.builder()
                .id(7)
                .name("New")
                .build();

        var existingStudy = StudyEntity.builder()
                .id(1)
                .name("Study test")
                .project(currentProject)
                .status(StudyStatus.IN_PROGRESS)
                .build();

        var dto = StudyDTO.builder()
                .id(1)
                .name("Study test")
                .project("New")
                .build();
        
        when(studyRepository.findById(1)).thenReturn(Optional.of(existingStudy));
        when(projectRepository.findByName("New")).thenReturn(Optional.of(currentProject));
        when(studyRepository.existsByNameAndProjectName("Study test", "New")).thenReturn(true);
        
        var ex = assertThrows(BusinessException.class,
                () -> studyServiceImpl.updateStudy(1, dto));

        assertEquals("A study with the same name already exists for the given project.", ex.getMessage());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }
}


