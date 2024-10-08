package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.ProjectEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyEntity;
import com.rte_france.antares.datamanager_back.repository.model.StudyStatus;
import com.rte_france.antares.datamanager_back.service.impl.StudyServiceImpl;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudyControllerTest {

   @Autowired
   protected WebApplicationContext wac;

   protected MockMvc mockMvc;

   @MockBean
   StudyServiceImpl studyService;

   @BeforeEach
   public void setup() {
      this.mockMvc = MockMvcBuilders
              .webAppContextSetup(wac)
              .build();
   }

    @Test
     void getTrajectoriesReturnsPageOfStudies() throws Exception {
       StudyEntity studyEntity = StudyEntity.builder().id(1).name("name").status(StudyStatus.IN_PROGRESS)
               .project(ProjectEntity.builder().name("project").build())
               .build();
        when(studyService.findStudiesByCriteria(any(),any())).thenReturn(new PageImpl<>(Collections.singletonList(studyEntity)));
       this.mockMvc.perform(get("/v1/study/search")
                       .contentType(MediaType.APPLICATION_JSON_VALUE)
                       .param("search", "toto")
                       .param("page", "1")
                       .param("size", "2")
                       .accept(MediaType.APPLICATION_JSON_VALUE))

               //Then
               .andExpect(status().isOk())
               .andDo(MockMvcResultHandlers.print())
               .andReturn();
       verify(studyService, times(1)).findStudiesByCriteria(any(), any());
    }

    @Test
     void getTrajectoriesReturnsEmptyPageWhenNoStudiesFound() throws Exception {
        when(studyService.findStudiesByCriteria(any(),any())).thenReturn(Page.empty());
        this.mockMvc.perform(get("/v1/study/search")
                       .contentType(MediaType.APPLICATION_JSON_VALUE)
                       .param("search", "toto")
                       .param("page", "1")
                       .param("size", "2")
                       .accept(MediaType.APPLICATION_JSON_VALUE))

               //Then
               .andExpect(status().isOk())
               .andDo(MockMvcResultHandlers.print())
               .andReturn();
       verify(studyService, times(1)).findStudiesByCriteria(any(), any());
    }
}
