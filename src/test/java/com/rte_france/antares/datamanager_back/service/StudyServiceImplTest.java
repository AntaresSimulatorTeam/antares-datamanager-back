package com.rte_france.antares.datamanager_back.service;
import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.exception.TechnicalException;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.StudyRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.study.impl.StudyServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import com.rte_france.antares.datamanager_back.service.study.StudyGeneratorService;
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
import java.util.*;

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

    @InjectMocks
    private TrajectoryServiceImpl trajectoryServiceImpl;

    @InjectMocks
    private UserService userService;

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

        String studyName = "Study 1-" + currentYear + "-" + nextYear + "thermalClusterRef";
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
    void updateStudy_throwsConflict_whenSameNameExistsInCurrentProject() {
        var currentProject = ProjectEntity.builder().id(7).name("Project").build();
        var study = StudyEntity.builder()
                .id(1)
                .name("MyStudy_2030")
                .project(currentProject)
                .horizon("2030")
                .status(StudyStatus.IN_PROGRESS)
                .build();
        var dto = StudyDTO.builder().id(1).name("MyStudy2").horizon("2030").project("Project").projectId("7").build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(projectRepository.findByName("Project")).thenReturn(Optional.of(currentProject));
        when(studyRepository.existsByNameAndProjectName("MyStudy2_2030", "Project")).thenReturn(true);

        var ex = assertThrows(BusinessException.class,
                () -> studyServiceImpl.updateStudy(1, dto));

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    void updateStudy_updatesProjectSuccessfully_whenProjectIsProvided() {
        var oldProject = ProjectEntity.builder().id(1).name("OldProject").build();
        var newProject = ProjectEntity.builder().id(2).name("NewProject").build();

        var study = StudyEntity.builder()
                .id(1)
                .name("MyStudy_2030")
                .project(oldProject)
                .status(StudyStatus.IN_PROGRESS)
                .build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(projectRepository.findByName("NewProject")).thenReturn(Optional.of(newProject));
        when(studyRepository.existsByNameAndProjectName("MyStudy_2030", "NewProject")).thenReturn(false);
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(study);

        var dto = StudyDTO.builder().project("NewProject").build();

        StudyDTO result = studyServiceImpl.updateStudy(1, dto);

        assertNotNull(result);
        verify(studyRepository).save(any(StudyEntity.class));
        verify(projectRepository).findByName("NewProject");
    }

    @Test
    void updateStudy_updatesNameSuccessfully_whenNameIsProvided() {
        var project = ProjectEntity.builder().id(1).name("Project").build();

        var study = StudyEntity.builder()
                .id(1)
                .name("OldName_2030")
                .project(project)
                .status(StudyStatus.IN_PROGRESS)
                .horizon("2030")
                .build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(projectRepository.findByName("Project")).thenReturn(Optional.of(project));
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(study);

        var dto = StudyDTO.builder().name("NewName").horizon("2030").project("Project").build();

        StudyDTO result = studyServiceImpl.updateStudy(1, dto);

        assertNotNull(result);
        verify(studyRepository).save(any(StudyEntity.class));
    }

    @Test
    void updateStudy_updatesTagsSuccessfully_whenTagsAreProvided() {
        var project = ProjectEntity.builder().id(1).name("Project").build();

        var study = StudyEntity.builder()
                .id(1)
                .name("MyStudy_2030")
                .project(project)
                .status(StudyStatus.IN_PROGRESS)
                .tags(List.of("tag1", "tag2"))
                .build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(study);

        var dto = StudyDTO.builder().tags(List.of("newTag1", "newTag2", "newTag3")).build();

        StudyDTO result = studyServiceImpl.updateStudy(1, dto);

        assertNotNull(result);
        verify(studyRepository).save(any(StudyEntity.class));
    }

    @Test
    void updateStudy_updatesMultipleFieldsSuccessfully() {
        var oldProject = ProjectEntity.builder().id(1).name("OldProject").build();
        var newProject = ProjectEntity.builder().id(2).name("NewProject").build();

        var study = StudyEntity.builder()
                .id(1)
                .name("OldName_2030")
                .project(oldProject)
                .status(StudyStatus.IN_PROGRESS)
                .horizon("2030")
                .tags(List.of("oldTag"))
                .build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(projectRepository.findByName("NewProject")).thenReturn(Optional.of(newProject));
        when(studyRepository.existsByNameAndProjectName(anyString(), eq("NewProject"))).thenReturn(false);
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(study);

        var dto = StudyDTO.builder()
                .id(1)
                .project("NewProject")
                .name("NewName")
                .tags(List.of("newTag1", "newTag2"))
                .horizon("2030")
                .build();

        StudyDTO result = studyServiceImpl.updateStudy(1, dto);

        assertNotNull(result);
        verify(studyRepository).save(any(StudyEntity.class));
        verify(projectRepository).findByName("NewProject");
    }

    @Test
    void updateStudy_doesNotUpdateAnyField_whenAllFieldsAreNull() {
        var project = ProjectEntity.builder().id(1).name("Project").build();

        var study = StudyEntity.builder()
                .id(1)
                .name("MyStudy_2030")
                .project(project)
                .status(StudyStatus.IN_PROGRESS)
                .tags(List.of("tag1"))
                .build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(study);

        var dto = StudyDTO.builder().build();

        StudyDTO result = studyServiceImpl.updateStudy(1, dto);

        assertNotNull(result);
        verify(studyRepository).save(any(StudyEntity.class));
        verify(projectRepository, never()).findByName(anyString());
    }

    @Test
    void updateStudy_updatesOnlyProject_whenOnlyProjectIsProvided() {
        var oldProject = ProjectEntity.builder().id(1).name("OldProject").build();
        var newProject = ProjectEntity.builder().id(2).name("NewProject").build();

        var study = StudyEntity.builder()
                .id(1)
                .name("MyStudy_2030")
                .project(oldProject)
                .status(StudyStatus.IN_PROGRESS)
                .tags(List.of("tag1"))
                .build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(projectRepository.findByName("NewProject")).thenReturn(Optional.of(newProject));
        when(studyRepository.existsByNameAndProjectName("MyStudy_2030", "NewProject")).thenReturn(false);
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(study);

        var dto = StudyDTO.builder().project("NewProject").build();

        StudyDTO result = studyServiceImpl.updateStudy(1, dto);

        assertNotNull(result);
        verify(studyRepository).save(any(StudyEntity.class));
        verify(projectRepository).findByName("NewProject");
    }

    @Test
    void updateStudy_updatesOnlyTags_whenOnlyTagsAreProvided() {
        var project = ProjectEntity.builder().id(1).name("Project").build();

        var study = StudyEntity.builder()
                .id(1)
                .name("MyStudy_2030")
                .project(project)
                .status(StudyStatus.IN_PROGRESS)
                .tags(List.of("oldTag"))
                .build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(study);

        var dto = StudyDTO.builder().tags(List.of("newTag1", "newTag2")).build();

        StudyDTO result = studyServiceImpl.updateStudy(1, dto);

        assertNotNull(result);
        verify(studyRepository).save(any(StudyEntity.class));
        verify(projectRepository, never()).findByName(anyString());
    }

    @Test
    void updateStudy_updatesNameAndTags_whenBothAreProvided() {
        var project = ProjectEntity.builder().id(1).name("Project").build();

        var study = StudyEntity.builder()
                .id(1)
                .name("OldName_2030")
                .project(project)
                .status(StudyStatus.IN_PROGRESS)
                .horizon("2030")
                .tags(List.of("oldTag"))
                .build();

        when(studyRepository.findById(1)).thenReturn(Optional.of(study));
        when(projectRepository.findByName("Project")).thenReturn(Optional.of(project));
        when(studyRepository.save(any(StudyEntity.class))).thenReturn(study);

        var dto = StudyDTO.builder()
                .name("NewName")
                .project("Project")
                .horizon("2056")
                .tags(List.of("newTag1", "newTag2"))
                .build();

        StudyDTO result = studyServiceImpl.updateStudy(1, dto);

        assertNotNull(result);
        verify(studyRepository).save(any(StudyEntity.class));
    }

    @Test
    void findStudiesByCriteria_returnsAllStudiesWhenSearchAndIdProjectAreNull() {
        Pageable pageable = PageRequest.of(0, 10);
        List<StudyEntity> studies = List.of(new StudyEntity());
        Page<StudyEntity> studyPage = new PageImpl<>(studies);

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(null, null, pageable);

        assertEquals(studyPage, result);
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_returnsEmptyPageWhenNoResultsFound() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<StudyEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        String search = "nonexistent";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertEquals(0, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_searchesByNameIgnoringCase() {
        Pageable pageable = PageRequest.of(0, 10);
        StudyEntity study = new StudyEntity();
        study.setName("Test Study");
        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study));
        String search = "TEST";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_searchesByCreatedBy() {
        Pageable pageable = PageRequest.of(0, 10);
        StudyEntity study = new StudyEntity();
        study.setCreatedBy("john.doe");
        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study));
        String search = "john";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_searchesByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        StudyEntity study = new StudyEntity();
        study.setStatus(StudyStatus.IN_PROGRESS);
        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study));
        String search = "progress";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_searchesByHorizon() {
        Pageable pageable = PageRequest.of(0, 10);
        StudyEntity study = new StudyEntity();
        study.setHorizon("2030");
        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study));
        String search = "2030";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_searchesByProjectName() {
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project = new ProjectEntity();
        project.setName("Project Alpha");
        StudyEntity study = new StudyEntity();
        study.setProject(project);
        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study));
        String search = "alpha";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_searchesByTags() {
        Pageable pageable = PageRequest.of(0, 10);
        StudyEntity study = new StudyEntity();
        study.setTags(List.of("important", "urgent"));
        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study));
        String search = "important";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_filtersCorrectlyBySpecificProjectId() {
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project1 = new ProjectEntity();
        project1.setId(1);
        ProjectEntity project2 = new ProjectEntity();
        project2.setId(2);

        StudyEntity study1 = new StudyEntity();
        study1.setProject(project1);

        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study1));
        Integer idProject = 1;

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(null, idProject, pageable);

        assertEquals(1, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_combinesSearchAndProjectFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project = new ProjectEntity();
        project.setId(1);
        project.setName("Project Alpha");

        StudyEntity study = new StudyEntity();
        study.setName("Test Study");
        study.setProject(project);

        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study));
        String search = "test";
        Integer idProject = 1;

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, idProject, pageable);

        assertEquals(1, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_handlesEmptyStringSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        List<StudyEntity> studies = List.of(new StudyEntity());
        Page<StudyEntity> studyPage = new PageImpl<>(studies);
        String search = "   ";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertEquals(studyPage, result);
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_returnsDistinctResults() {
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project = new ProjectEntity();
        project.setId(1);

        StudyEntity study1 = new StudyEntity();
        study1.setId(1);
        study1.setProject(project);

        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study1));
        String search = "test";
        Integer idProject = 1;

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, idProject, pageable);

        // Vérifie que la specification est appelée (qui contient query.distinct(true))
        assertEquals(1, result.getTotalElements());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_handlesPaginationCorrectly() {
        Pageable pageable = PageRequest.of(1, 5);
        List<StudyEntity> studies = List.of(
                new StudyEntity(),
                new StudyEntity(),
                new StudyEntity()
        );
        Page<StudyEntity> studyPage = new PageImpl<>(studies, pageable, 15);

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria("test", null, pageable);

        assertEquals(3, result.getNumberOfElements());
        assertEquals(15, result.getTotalElements());
        assertEquals(1, result.getNumber());
        assertEquals(5, result.getSize());
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findStudiesByCriteria_handlesSpecialCharactersInSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        StudyEntity study = new StudyEntity();
        study.setName("Study-Test_2024");
        Page<StudyEntity> studyPage = new PageImpl<>(List.of(study));
        String search = "Study-Test";

        when(studyRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(studyPage);

        Page<StudyEntity> result = studyServiceImpl.findStudiesByCriteria(search, null, pageable);

        assertNotNull(result);
        verify(studyRepository).findAll(any(Specification.class), eq(pageable));
    }
}


