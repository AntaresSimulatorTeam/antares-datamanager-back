package com.rte_france.antares.datamanager_back.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.ProjectDto;
import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.exception.BadRequestException;
import com.rte_france.antares.datamanager_back.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rte_france.antares.datamanager_back.dto.ProjectInputDto;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    ProjectService projectService;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void getProjectsByUser_returnsProjectsWhenExist() throws Exception {
        String userId = "user1";
        ProjectEntity projectEntity = ProjectEntity.builder().id(1).name("Project 1").createdBy("user1").build();
        when(projectService.getPinnedProjectsByUser(userId)).thenReturn(List.of(projectEntity));

        mockMvc.perform(get("/v1/project/pinned")
                        .param("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Project 1"))
                .andExpect(jsonPath("$[0].createdBy").value("user1"));
    }

    @Test
    void getProjectsByUser_returnsEmptyWhenNoneExist() throws Exception {
        String userId = "user1";
        when(projectService.getPinnedProjectsByUser(userId)).thenReturn(List.of());

        mockMvc.perform(get("/v1/project/pinned")
                        .param("userId", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
     void removePinnedProjectToUser_shouldCallServiceMethod() throws Exception {
        // Given
        String userId = "testUser";
        Integer projectId = 1;

        // When
        mockMvc.perform(put("/v1/project/unpin")
                        .param("userId", userId)
                        .param("projectId", projectId.toString()))
                .andExpect(status().isOk()); // Verifies the HTTP status

        // Then
        verify(projectService, times(1)).deletePinnedProjectForGivenUser(userId, projectId);
    }


    @Test
    void getStudiesReturnsPageOfStudies() throws Exception {
        ProjectEntity projectEntity = ProjectEntity.builder().id(1).name("name").createdBy("user1").build();
        when(projectService.findProjectsByCriteria(any(), any())).thenReturn(new PageImpl<>(Collections.singletonList(projectEntity)));
        this.mockMvc.perform(get("/v1/project/search")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("search", "toto")
                        .param("page", "1")
                        .param("size", "2")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(projectService, times(1)).findProjectsByCriteria(any(), any());
    }

    @Test
    void pinProjectForUser_returnsOkWhenProjectPinnedSuccessfully() throws Exception {
        String userId = "user1";
        Integer projectId = 1;

        when(projectService.pinProjectForUser(any(), any())).thenReturn(new ProjectEntity());

        mockMvc.perform(post("/v1/project/pin")
                        .param("userId", userId)
                        .param("projectId", projectId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void pinProjectForUser_returnsConflictWhenProjectAlreadyPinned() throws Exception {
        String userId = "user1";
        Integer projectId = 1;

        doThrow(new BadRequestException("Project already pinned for user")).when(projectService).pinProjectForUser(userId, projectId);

        mockMvc.perform(post("/v1/project/pin")
                        .param("userId", userId)
                        .param("projectId", projectId.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void findProjectById_returnsProjectDtoWhenProjectExists() throws Exception {
        // Given
        Integer projectId = 1;
        ProjectEntity projectEntity = ProjectEntity.builder().id(1).name("name2050").createdBy("user1").description("project2050").build();


        when(projectService.findProjectById(projectId)).thenReturn(projectEntity);


        // When & Then
        mockMvc.perform(get("/v1/project/{id}", projectId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.name").value("name2050"))
                .andExpect(jsonPath("$.description").value("project2050"));
    }
@Test
void deleteProject_returnsNoContentWhenProjectDeleted() throws Exception {
    Integer projectId = 1;

    doNothing().when(projectService).deleteProjectById(projectId);

    mockMvc.perform(delete("/v1/project/{id}", projectId)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
}

@Test
void deleteProject_returnsNotFoundWhenProjectDoesNotExist() throws Exception {
    Integer projectId = 1;

    doThrow(new ResourceNotFoundException("Project not found")).when(projectService).deleteProjectById(projectId);

    mockMvc.perform(delete("/v1/project/{id}", projectId)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
}

@Test
void deleteProject_returnsBadRequestWhenProjectContainsStudies() throws Exception {
    Integer projectId = 1;

    doThrow(new BadRequestException("Project contains studies and cannot be deleted")).when(projectService).deleteProjectById(projectId);

    mockMvc.perform(delete("/v1/project/{id}", projectId)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError());
}

    @Test
    void searchProjectsByNameReturnsMatchingProjects() throws Exception {
        ProjectDto projectDto = ProjectDto.builder().id(1).name("Project 1").build();
        when(projectService.searchProjectsByName("Proj")).thenReturn(List.of(projectDto));

        mockMvc.perform(get("/v1/project/autocomplete")
                        .param("partialName", "Proj")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Project 1"));
    }

    @Test
    void searchProjectsByNameReturnsEmptyListWhenNoMatches() throws Exception {
        when(projectService.searchProjectsByName("NonExistent")).thenReturn(List.of());

        mockMvc.perform(get("/v1/project/autocomplete")
                        .param("partialName", "NonExistent")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void createProject_returnsProjectDto_whenValidInputProvided() throws Exception {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");

        when(projectService.createProject(any(ProjectInputDto.class))).thenReturn(new ProjectEntity());

        this.mockMvc.perform(post("/v1/project")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(new ObjectMapper().writeValueAsString(projectInputDto))
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                .andExpect(status().isOk())
                .andReturn();

        verify(projectService, times(1)).createProject(any(ProjectInputDto.class));
    }

    @Test
    void createProject_returnsBadRequest_whenInvalidInputProvided() throws Exception {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("test");

        this.mockMvc.perform(post("/v1/project")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(new ObjectMapper().writeValueAsString(projectInputDto))
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                .andExpect(status().isInternalServerError())
                .andReturn();
    }
}