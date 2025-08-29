package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.PinnedProjectRepository;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntityId;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.impl.ProjectServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import com.rte_france.antares.datamanager_back.util.Utils;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private PinnedProjectRepository pinnedProjectRepository;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserService userService;

    @Test
    void findProjectsByCriteria_returnsAllProjectsWhenSearchIsEmpty() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProjectEntity> expectedPage = new PageImpl<>(List.of(ProjectEntity.builder().name("project1").build()), pageable, 1);

        when(projectRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria("", pageable);

        assertEquals(expectedPage, result);
        verify(projectRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findProjectsByCriteria_filtersByName() {
        String search = "project1";
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project = ProjectEntity.builder().name("project1").build();
        Page<ProjectEntity> expectedPage = new PageImpl<>(List.of(project), pageable, 1);

        when(projectRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(search, pageable);

        assertEquals(expectedPage, result);
        verify(projectRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findProjectsByCriteria_filtersByCreatedBy() {
        String search = "user1";
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project = ProjectEntity.builder().createdBy("user1").build();
        Page<ProjectEntity> expectedPage = new PageImpl<>(List.of(project), pageable, 1);

        when(projectRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(search, pageable);

        assertEquals(expectedPage, result);
        verify(projectRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findProjectsByCriteria_filtersByTag() {
        String search = "tag1";
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project = ProjectEntity.builder().tags(List.of("tag1")).build();
        Page<ProjectEntity> expectedPage = new PageImpl<>(List.of(project), pageable, 1);

        when(projectRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(search, pageable);

        assertEquals(expectedPage, result);
        verify(projectRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findProjectsByCriteria_filtersByStudyName() {
        String search = "study1";
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project = ProjectEntity.builder()
                .studies(List.of(StudyEntity.builder().name("study1").build()))
                .build();
        Page<ProjectEntity> expectedPage = new PageImpl<>(List.of(project), pageable, 1);

        when(projectRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(search, pageable);

        assertEquals(expectedPage, result);
        verify(projectRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findProjectsByCriteria_filtersByCreationDate() {
        String search = "2023-01-01T00:00:00";
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project = ProjectEntity.builder().creationDate(Utils.parseToLocalDateTime(search)).build();
        Page<ProjectEntity> expectedPage = new PageImpl<>(List.of(project), pageable, 1);

        when(projectRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(search, pageable);

        assertEquals(expectedPage, result);
        verify(projectRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findProjectsByCriteria_returnsEmptyPageWhenNoMatch() {
        String search = "nonexistent";
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProjectEntity> expectedPage = new PageImpl<>(List.of(), pageable, 0);

        when(projectRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(search, pageable);

        assertEquals(expectedPage, result);
        verify(projectRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getProjectsByUser_returnsProjectsWhenExist() {
        String userId = "user1";
        PinnedProjectEntity pinnedProject = new PinnedProjectEntity();
        ProjectEntity project = new ProjectEntity();
        pinnedProject.setProject(project);
        when(pinnedProjectRepository.findByIdNni(userId)).thenReturn(List.of(pinnedProject));

        List<ProjectEntity> result = projectService.getPinnedProjectsByUser(userId);

        assertEquals(1, result.size());
        assertEquals(project, result.get(0));
    }

    @Test
    void getProjectsByUser_returnsEmptyWhenNoneExist() {
        String userId = "user1";
        when(pinnedProjectRepository.findByIdNni(userId)).thenReturn(List.of());

        List<ProjectEntity> result = projectService.getPinnedProjectsByUser(userId);

        assertEquals(0, result.size());
    }

    @Test
    void deletePinnedProjectToUser_shouldCallDeleteMethod() {
        // Given
        String userId = "userId";
        Integer projectId = 1;
        PinnedProjectEntityId pinnedProjectEntityId = new PinnedProjectEntityId(userId, projectId);
        when(pinnedProjectRepository.existsById(pinnedProjectEntityId)).thenReturn(true);
        // When
        projectService.deletePinnedProjectForGivenUser(userId, projectId);

        // Then
        verify(pinnedProjectRepository, times(1)).deletePinnedProjectEntityById(pinnedProjectEntityId);
    }

    @Test
    void deletePinnedProjectForGivenUser_shouldThrowException_whenProjectDoesNotExist() {
        // Given
        String userId = "testUser";
        Integer projectId = 2;
        PinnedProjectEntityId pinnedProjectEntityId = new PinnedProjectEntityId(userId, projectId);

        when(pinnedProjectRepository.existsById(pinnedProjectEntityId)).thenReturn(false);

        // Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.deletePinnedProjectForGivenUser(userId, projectId)
        );

        assertEquals("Pinned project not found for user: {0}, project ID: {1}", exception.getMessage());
        assertEquals(List.of("testUser","2"), exception.getErrorMessageArguments());
        verify(pinnedProjectRepository, never()).deletePinnedProjectEntityById(pinnedProjectEntityId);
    }

    @Test
    void getProjectDetailsById() {
        //Given
        Integer projectId = 1;
        ProjectEntity expectedProject = new ProjectEntity();
        expectedProject.setId(1);
        expectedProject.setCreatedBy("User1");
        expectedProject.setName("BP 2050");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(expectedProject));
        ProjectEntity projectResult = projectService.findProjectById(projectId);
        //Then
        assertEquals(projectResult, expectedProject);


    }

    @Test
    void pinProjectForUser_pinsProjectWhenNotAlreadyPinned() {
        String userId = "user1";
        Integer projectId = 1;
        PinnedProjectEntityId pinnedProjectEntityId = new PinnedProjectEntityId(userId, projectId);
        ProjectEntity projectEntity = new ProjectEntity();
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user1").firstName("JEAN").lastName("RORTEAU").build());
        when(pinnedProjectRepository.findById(pinnedProjectEntityId)).thenReturn(Optional.empty());
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(projectEntity));

        projectService.pinProjectForUser(userId, projectId);

        verify(pinnedProjectRepository, times(1)).save(any(PinnedProjectEntity.class));
    }

    @Test
    void pinProjectForUser_throwsExceptionWhenProjectAlreadyPinned() {
        String userId = "user1";
        Integer projectId = 1;
        PinnedProjectEntityId pinnedProjectEntityId = new PinnedProjectEntityId(userId, projectId);
        PinnedProjectEntity pinnedProjectEntity = new PinnedProjectEntity();
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user1").firstName("JEAN").lastName("RORTEAU").build());
        when(pinnedProjectRepository.findById(pinnedProjectEntityId)).thenReturn(Optional.of(pinnedProjectEntity));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.pinProjectForUser(userId, projectId)
        );

        assertEquals("Project already pinned", exception.getMessage());
        verify(pinnedProjectRepository, never()).save(any(PinnedProjectEntity.class));
    }

    @Test
    void pinProjectForUser_throwsExceptionWhenUserIdDoesNotMatchAuthenticatedUserId() {
        String userId = "user1";
        Integer projectId = 1;
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user2").firstName("JEAN").lastName("RORTEAU").build());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.pinProjectForUser(userId, projectId)
        );

        assertEquals("User ID does not match the authenticated user's ID.", exception.getMessage());
    }

    @Test
    void pinProjectForUser_throwsExceptionWhenProjectNotFound() {
        String userId = "user1";
        Integer projectId = 1;
        PinnedProjectEntityId pinnedProjectEntityId = new PinnedProjectEntityId(userId, projectId);
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user1").firstName("JEAN").lastName("RORTEAU").build());
        when(pinnedProjectRepository.findById(pinnedProjectEntityId)).thenReturn(Optional.empty());
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.pinProjectForUser(userId, projectId)
        );

        assertEquals("Project not found with ID: {0}", exception.getMessage());
        assertEquals(Collections.singletonList("1"), exception.getErrorMessageArguments());
        verify(pinnedProjectRepository, never()).save(any(PinnedProjectEntity.class));
    }

    @Test
    void pinProjectForUser_throwsExceptionWhenUserHasMaxPinnedProjects() {
        String userId = "user1";
        Integer projectId = 1;
        List<PinnedProjectEntity> pinnedProjects = List.of(new PinnedProjectEntity(), new PinnedProjectEntity(), new PinnedProjectEntity());
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userId).firstName("JEAN").lastName("RORTEAU").build());

        when(pinnedProjectRepository.findByIdNni(userId)).thenReturn(pinnedProjects);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.pinProjectForUser(userId, projectId)
        );

        assertEquals("Maximum number of pinned projects reached.", exception.getMessage());
        verify(pinnedProjectRepository, never()).save(any(PinnedProjectEntity.class));
    }

    @Test
    void deleteProjectById_deletesProjectWhenNoStudies() {
        Integer projectId = 1;
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        projectService.deleteProjectById(projectId);

        verify(projectRepository, times(1)).deleteById(projectId);
    }

    @Test
    void deleteProjectById_throwsExceptionWhenProjectNotFound() {
        Integer projectId = 1;

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.deleteProjectById(projectId)
        );

        assertEquals("Project not found with ID: {0}", exception.getMessage());
        assertEquals(Collections.singletonList("1"), exception.getErrorMessageArguments());
        verify(projectRepository, never()).deleteById(projectId);
    }

    @Test
    void deleteProjectById_throwsExceptionWhenProjectContainsStudies() {
        Integer projectId = 1;
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStudies(List.of(new StudyEntity()));

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.deleteProjectById(projectId)
        );

        assertEquals("Project contains studies and cannot be deleted", exception.getMessage());
        verify(projectRepository, never()).deleteById(projectId);
    }

    @Test
    void searchProjectsByNameReturnsMatchingProjects() {
        ProjectEntity projectEntity = new ProjectEntity();
        projectEntity.setId(1);
        projectEntity.setName("Project 1");
        when(projectRepository.findByNameContainingIgnoreCase("Proj")).thenReturn(List.of(projectEntity));

        List<ProjectDto> result = projectService.searchProjectsByName("Proj");

        assertEquals(1, result.size());
        assertEquals("Project 1", result.get(0).getName());
        verify(projectRepository, times(1)).findByNameContainingIgnoreCase("Proj");
    }

    @Test
    void searchProjectsByNameReturnsEmptyListWhenNoMatches() {
        when(projectRepository.findByNameContainingIgnoreCase("NonExistent")).thenReturn(List.of());

        List<ProjectDto> result = projectService.searchProjectsByName("NonExistent");

        assertEquals(0, result.size());
        verify(projectRepository, times(1)).findByNameContainingIgnoreCase("NonExistent");
    }

    @Test
    void searchProjectsByNameHandlesNullInput() {
        List<ProjectDto> result = projectService.searchProjectsByName(null);

        assertEquals(0, result.size());
        verify(projectRepository, times(1)).findByNameContainingIgnoreCase(null);
    }

    @Test
    void createProject_throwsException_whenProjectNameIsBlank() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.createProject(projectInputDto)
        );

        assertEquals("Project name is required.", exception.getMessage());
    }

    @Test
    void createProject_throwsException_whenProjectWithSameNameExists() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("existingProject");

        when(projectRepository.findByName(any(String.class))).thenReturn(Optional.of(new ProjectEntity()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.createProject(projectInputDto)
        );

        assertEquals("A project with the same name already exists.", exception.getMessage());
    }

    @Test
    void createProject_throwsException_whenTagsExceedLimit() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");
        projectInputDto.setTags(List.of("tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.createProject(projectInputDto)
        );

        assertEquals("A project cannot have more than 6 tags.", exception.getMessage());
    }

    @Test
    void createProject_createsProjectSuccessfully_whenValidInput() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");
        projectInputDto.setTags(List.of("tag1", "tag2"));
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("user2").firstName("JEAN").lastName("RORTEAU").build());

        when(projectRepository.findByName(any(String.class))).thenReturn(Optional.empty());
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(i -> i.getArguments()[0]);

        ProjectEntity projectEntity = projectService.createProject(projectInputDto);

        assertEquals(projectInputDto.getName(), projectEntity.getName());
        assertEquals(projectInputDto.getTags(), projectEntity.getTags());
    }


    @Test
    void updateProject_ShouldUpdateFields_WhenInputIsValid() {
        Integer projectId = 1;
        ProjectEntity existingProject = new ProjectEntity();
        existingProject.setId(projectId);

        ProjectInputDto inputDto = new ProjectInputDto();
        inputDto.setDescription("New description");
        inputDto.setTags(List.of("tag1", "tag2"));

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectEntity result = projectService.updateProject(projectId, inputDto);

        assertEquals("New description", result.getDescription());
        assertEquals(2, result.getTags().size());
    }

    @Test
    void updateProject_ShouldUpdateFields_WhenInputIsBlank() {
        Integer projectId = 1;
        ProjectEntity existingProject = new ProjectEntity();
        existingProject.setId(projectId);
        existingProject.setDescription("New description");
        existingProject.setTags(List.of("tag1", "tag2"));

        ProjectInputDto inputDto = new ProjectInputDto();
        inputDto.setDescription("");
        inputDto.setTags(List.of());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectEntity result = projectService.updateProject(projectId, inputDto);

        assertEquals("", result.getDescription());
        assertEquals(0, result.getTags().size());
    }

    @Test
    void updateProject_ShouldUpdateField_WhenInputIsNull() {
        Integer projectId = 1;
        ProjectEntity existingProject = new ProjectEntity();
        existingProject.setId(projectId);
        existingProject.setDescription("New description");
        existingProject.setTags(List.of("tag1", "tag2"));

        ProjectInputDto inputDto = new ProjectInputDto();
        inputDto.setDescription(null);
        inputDto.setTags(null);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectEntity result = projectService.updateProject(projectId, inputDto);

        assertEquals(existingProject.getDescription(), result.getDescription());
        assertEquals(existingProject.getTags().size(), result.getTags().size());
    }

    @Test
        void updateProject_ShouldThrowException_WhenProjectNotFound() {
        Integer projectId = 999;
        ProjectInputDto inputDto = new ProjectInputDto();

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
             () -> projectService.updateProject(projectId, inputDto));

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
        void updateProject_ShouldThrowException_WhenTagsExceedLimit() {
        Integer projectId = 1;
        ProjectEntity project = new ProjectEntity();

        ProjectInputDto inputDto = new ProjectInputDto();
        inputDto.setTags(List.of("t1", "t2", "t3", "t4", "t5", "t6", "t7"));

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        BusinessException ex = assertThrows(BusinessException.class,
              () -> projectService.updateProject(projectId, inputDto));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void findProjectById_ShouldReturnProject_WhenFound() {
        Integer projectId = 42;
        ProjectEntity expectedProject = new ProjectEntity();
        expectedProject.setId(projectId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(expectedProject));

        ProjectEntity result = projectService.findProjectById(projectId);

        assertNotNull(result);
        assertEquals(projectId, result.getId());
    }

    @Test
    void findProjectById_ShouldThrowException_WhenNotFound() {
        Integer projectId = 999;

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            projectService.findProjectById(projectId);
        });

        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("Project not found with ID:"));
    }

    @Test
    void findProjectById_ShouldThrowException_WhenSameProjectNameExists() {
        Integer projectId = 1;
        String existingName = "Existing Project";

        ProjectEntity existingProject = new ProjectEntity();
        existingProject.setId(projectId);
        existingProject.setName("Old Name");

        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName(existingName);
        
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
        when(projectRepository.findByName(existingName)).thenReturn(Optional.of(new ProjectEntity()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> projectService.updateProject(projectId, projectInputDto)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
        assertEquals("A project with the same name already exists.", exception.getMessage());
    }

    @Test
    void findProjectsByCriteria_ShouldReturnResults_WhenSearchIsValid() {
        String search = "biology";
        Pageable paging = PageRequest.of(0, 10);
        Page<ProjectEntity> mockPage = new PageImpl<>(List.of(new ProjectEntity()));

        when(projectRepository.findAll(any(Specification.class), eq(paging))).thenReturn(mockPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(search, paging);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(projectRepository).findAll(any(Specification.class), eq(paging));
    }

    @Test
    void findProjectsByCriteria_ShouldReturnEmpty_WhenSearchIsBlank() {
        String search = " ";
        Pageable paging = PageRequest.of(0, 5);
        Page<ProjectEntity> emptyPage = new PageImpl<>(Collections.emptyList());

        when(projectRepository.findAll(any(Specification.class), eq(paging))).thenReturn(emptyPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(search, paging);

        assertTrue(result.isEmpty());
    }

    @Test
    void findProjectsByCriteria_ShouldHandleDateParsing_WhenSearchIsDate() {
        String search = "2023-07-01T10:30:00";
        Pageable paging = PageRequest.of(0, 5);
        Page<ProjectEntity> mockPage = new PageImpl<>(List.of(new ProjectEntity()));

        when(projectRepository.findAll(any(Specification.class), eq(paging))).thenReturn(mockPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(search, paging);

        assertFalse(result.isEmpty());
    }
}
