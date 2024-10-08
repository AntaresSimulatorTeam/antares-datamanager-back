package com.rte_france.antares.datamanager_back.controller;

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
import org.springframework.http.MediaType;

import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void createProject_returnsProjectDto_whenValidInputProvided() throws Exception {
        ProjectInputDto projectInputDto = new ProjectInputDto();
        projectInputDto.setName("testProject");
        projectInputDto.setStudyIds(List.of(1, 2));

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
        projectInputDto.setStudyIds(List.of());

        this.mockMvc.perform(post("/v1/project")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(new ObjectMapper().writeValueAsString(projectInputDto))
                .accept(MediaType.APPLICATION_JSON_VALUE))

                .andExpect(status().isInternalServerError())
                .andReturn();
    }
}
