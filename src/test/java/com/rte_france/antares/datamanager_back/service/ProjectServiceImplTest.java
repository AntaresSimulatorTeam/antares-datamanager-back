package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.rte_france.antares.datamanager_back.repository.PinnedProjectRepository;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.PinnedProjectEntityId;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.service.impl.ProjectServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    private PinnedProjectRepository pinnedProjectRepository;

    @InjectMocks
    private ProjectServiceImpl projectService;

    @Test
    void getProjectsByUser_returnsProjectsWhenExist() {
        String userId = "user1";
        PinnedProjectEntity pinnedProject = new PinnedProjectEntity();
        ProjectEntity project = new ProjectEntity();
        pinnedProject.setProject(project);
        when(pinnedProjectRepository.findById_Nni(userId)).thenReturn(List.of(pinnedProject));

        List<ProjectEntity> result = projectService.getPinnedProjectsByUser(userId);

        assertEquals(1, result.size());
        assertEquals(project, result.get(0));
    }

    @Test
    void getProjectsByUser_returnsEmptyWhenNoneExist() {
        String userId = "user1";
        when(pinnedProjectRepository.findById_Nni(userId)).thenReturn(List.of());

        List<ProjectEntity> result = projectService.getPinnedProjectsByUser(userId);

        assertEquals(0, result.size());
    }

    @Test
    public void deletePinnedProjectToUser_shouldCallDeleteMethod() {
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


}
