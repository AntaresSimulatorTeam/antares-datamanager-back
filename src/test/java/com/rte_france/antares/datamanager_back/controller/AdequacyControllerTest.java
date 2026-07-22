package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.adequacy.AdequacyFileProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdequacyControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    AdequacyFileProcessorService adequacyFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void uploadAdequacyTrajectory_returnsCreatedTrajectory() throws Exception {
        when(adequacyFileProcessorService.processAdequacyFile(anyString(), anyString(), anyInt(), anyBoolean()))
                .thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory/adequacy-patch")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "test")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .param("isCivilYear", "true")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print());

        verify(adequacyFileProcessorService, times(1)).processAdequacyFile(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void uploadAdequacyTrajectory_returnsBadRequestForInvalidHorizon() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/adequacy-patch")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "test")
                        .param("horizon", "invalid-horizon")
                        .param("studyId", "1")
                        .param("isCivilYear", "true")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());

        verify(adequacyFileProcessorService, never()).processAdequacyFile(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void uploadAdequacyTrajectory_returnsBadRequestForTooLongTrajectoryName() throws Exception {
        String longName = "a".repeat(41);
        this.mockMvc.perform(post("/v1/trajectory/adequacy-patch")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", longName)
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .param("isCivilYear", "true")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());

        verify(adequacyFileProcessorService, never()).processAdequacyFile(anyString(), anyString(), anyInt(), anyBoolean());
    }
}
