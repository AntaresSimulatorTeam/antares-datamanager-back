package com.rte_france.antares.datamanager_back.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rte_france.antares.datamanager_back.dto.StudyDTO;
import com.rte_france.antares.datamanager_back.exception.BadRequestException;
import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyStatus;
import com.rte_france.antares.datamanager_back.service.impl.StudyGeneratorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.StudyServiceImpl;
import com.rte_france.antares.datamanager_back.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
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
class StudyControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    StudyServiceImpl studyService;

    @MockBean
    StudyGeneratorServiceImpl studyGeneratorService;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void getStudiesReturnsPageOfStudies() throws Exception {
        StudyEntity studyEntity = StudyEntity.builder().id(1).name("name").status(StudyStatus.IN_PROGRESS)
                .project(ProjectEntity.builder().name("project").build())
                .build();
        when(studyService.findStudiesByCriteria(any(), any(), any())).thenReturn(new PageImpl<>(Collections.singletonList(studyEntity)));
        this.mockMvc.perform(get("/v1/study/search")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("search", "toto")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sortColumn", "createdBy")
                        .param("sortDirection", "desc")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(studyService, times(1)).findStudiesByCriteria(any(), any(), any());
    }

    @Test
    void getStudiesReturnsEmptyPageWhenNoStudiesFound() throws Exception {
        when(studyService.findStudiesByCriteria(any(), any(), any())).thenReturn(Page.empty());
        this.mockMvc.perform(get("/v1/study/search")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("search", "toto")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sortColumn", "createdBy")
                        .param("sortDirection", "desc")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isOk())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(studyService, times(1)).findStudiesByCriteria(any(), any(), any());
    }

    @Test
    void searchKeywordsByPartialNameReturnsMatchingKeywords() throws Exception {
        when(studyService.searchKeywordsByPartialName("key")).thenReturn(List.of("keyword1", "keyword2"));

        mockMvc.perform(get("/v1/study/keywords/search")
                        .param("partialName", "key")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("keyword1"))
                .andExpect(jsonPath("$[1]").value("keyword2"));
    }

    @Test
    void searchKeywordsByPartialNameReturnsEmptyListWhenNoMatches() throws Exception {
        when(studyService.searchKeywordsByPartialName("nonExistent")).thenReturn(List.of());

        mockMvc.perform(get("/v1/study/keywords/search")
                        .param("partialName", "nonExistent")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }


    @Test
    void createStudyReturnsCreatedStudy() throws Exception {
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").build();
        StudyDTO createdStudyDTO = StudyDTO.builder().id(1).name("Study 1").createdBy("User 1").build();

        when(studyService.createStudy(any(StudyDTO.class))).thenReturn(createdStudyDTO);

        this.mockMvc.perform(post("/v1/study")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(Utils.asJsonString(studyDTO))
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Study 1"))
                .andExpect(jsonPath("$.createdBy").value("User 1"))
                .andDo(MockMvcResultHandlers.print())
                .andReturn();

        verify(studyService, times(1)).createStudy(any(StudyDTO.class));
    }

    @Test
    void createStudyThrowsBadRequestWhenNoProjectInfoProvided() throws Exception {
        StudyDTO studyDTO = StudyDTO.builder().name("Study 1").createdBy("User 1").build();

        when(studyService.createStudy(any(StudyDTO.class))).thenThrow(new BadRequestException("Either project name or project ID must be provided."));

        this.mockMvc.perform(post("/v1/study")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(Utils.asJsonString(studyDTO))
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isInternalServerError())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();

        verify(studyService, times(1)).createStudy(any(StudyDTO.class));
    }

    @Test
    void deleteStudyByIdReturnsNoContentWhenStudyExists() throws Exception {
        doNothing().when(studyService).deleteStudyById(1);

        this.mockMvc.perform(delete("/v1/study/1")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNoContent())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();

        verify(studyService, times(1)).deleteStudyById(1);
    }

    @Test
    void deleteStudyByIdThrowsBadRequestWhenStudyNotFound() throws Exception {
        doThrow(new BadRequestException("Study with id 1 not found.")).when(studyService).deleteStudyById(1);

        this.mockMvc.perform(delete("/v1/study/1")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isInternalServerError())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();

        verify(studyService, times(1)).deleteStudyById(1);
    }

    @Test
    void generateStudySuccess() throws Exception {
        Integer studyId = 1234;

        this.mockMvc.perform(post("/v1/study/generate")
                        .param("id", String.valueOf(studyId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(Utils.asJsonString(studyId))
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();

        verify(studyGeneratorService, times(1)).buildJsonForStudyGeneration(eq(studyId));
        verify(studyGeneratorService, times(1)).callGenerateStudyService(eq(studyId));
    }

    @Test
    void generateStudyUpdatesStatusWhenSuccessful() throws Exception {
        Integer studyId = 1234;

        this.mockMvc.perform(post("/v1/study/generate")
                        .param("id", String.valueOf(studyId))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(Utils.asJsonString(studyId))
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();

        verify(studyGeneratorService, times(1)).buildJsonForStudyGeneration(eq(studyId));
        verify(studyGeneratorService, times(1)).callGenerateStudyService(eq(studyId));
        verify(studyService, times(1)).updateStudyStatusAsGenerated(eq(studyId));
    }
}
