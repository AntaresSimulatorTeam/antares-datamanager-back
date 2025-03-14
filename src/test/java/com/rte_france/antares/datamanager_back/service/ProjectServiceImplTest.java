package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.exception.BadRequestException;
import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.repository.PinnedProjectRepository;
import com.rte_france.antares.datamanager_back.repository.ProjectRepository;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntityId;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.service.impl.ProjectServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void findProjectsByCriteria_returnsAllProjectsWhenSearchIsNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ProjectEntity> expectedPage = new PageImpl<>(List.of(ProjectEntity.builder().name("project1").build()), pageable, 1);

        when(projectRepository.findAll(pageable)).thenReturn(expectedPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(null, pageable);

        assertEquals(expectedPage, result);
        verify(projectRepository, times(1)).findAll(pageable);
    }

    @Test
    void findProjectsByCriteria_returnsProjectsByStudyName() {
        String studyName = "study1";
        Pageable pageable = PageRequest.of(0, 10);
        ProjectEntity project = ProjectEntity.builder()
                .name("project1")
                .studies(Collections.singletonList(StudyEntity.builder().name(studyName).build()))
                .build();
        Page<ProjectEntity> expectedPage = new PageImpl<>(List.of(project), pageable, 1);

        when(projectRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<ProjectEntity> result = projectService.findProjectsByCriteria(studyName, pageable);

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
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> projectService.deletePinnedProjectForGivenUser(userId, projectId)
        );

        assertEquals("Pinned project not found for user: testUser, project ID: 2", exception.getMessage());
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

        BadRequestException exception = assertThrows(
                BadRequestException.class,
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

        BadRequestException exception = assertThrows(
                BadRequestException.class,
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

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> projectService.pinProjectForUser(userId, projectId)
        );

        assertEquals("Project not found with ID: 1", exception.getMessage());
        verify(pinnedProjectRepository, never()).save(any(PinnedProjectEntity.class));
    }

    @Test
    void pinProjectForUser_throwsExceptionWhenUserHasMaxPinnedProjects() {
        String userId = "user1";
        Integer projectId = 1;
        List<PinnedProjectEntity> pinnedProjects = List.of(new PinnedProjectEntity(), new PinnedProjectEntity(), new PinnedProjectEntity());
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni(userId).firstName("JEAN").lastName("RORTEAU").build());

        when(pinnedProjectRepository.findByIdNni(userId)).thenReturn(pinnedProjects);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
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

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> projectService.deleteProjectById(projectId)
        );

        assertEquals("Project not found with ID: 1", exception.getMessage());
        verify(projectRepository, never()).deleteById(projectId);
    }

    @Test
    void deleteProjectById_throwsExceptionWhenProjectContainsStudies() {
        Integer projectId = 1;
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStudies(List.of(new StudyEntity()));

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
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

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.createProject(projectInputDto)
        );

        assertEquals("Project name is required.", exception.getMessage());
    }

    @Test
    void createProject_throwsException_whenProjectWithSameNameExists() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("existingProject");

        when(projectRepository.findByName(any(String.class))).thenReturn(Optional.of(new ProjectEntity()));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.createProject(projectInputDto)
        );

        assertEquals("A project with the same name already exists.", exception.getMessage());
    }

    @Test
    void createProject_throwsException_whenTagsExceedLimit() {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");
        projectInputDto.setTags(List.of("tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
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
}
