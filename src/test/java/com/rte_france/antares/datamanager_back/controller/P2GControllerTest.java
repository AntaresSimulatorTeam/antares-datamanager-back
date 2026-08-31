package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.p2g.P2gFileProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class P2GControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    private P2gFileProcessorService p2gFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void postValidRequest_should_returnCreated_and_callService() throws Exception {
        // Given
        String trajectoryToUse = "FE_Liv_test.xls";
        String horizon = "2025-2026";
        String studyId = "1";
        String isCivilYear = "true";

        TrajectoryEntity fakeEntity = mock(TrajectoryEntity.class);
        when(p2gFileProcessorService.processModulationP2gFile(anyString(), anyString(), anyInt(), anyBoolean()))
                .thenReturn(fakeEntity);


        // When / Then
        mockMvc.perform(post("/v1/trajectory/modulation-p2g")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", studyId)
                        .param("isCivilYear", isCivilYear)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        verify(p2gFileProcessorService, times(1))
                .processModulationP2gFile(anyString(), anyString(), anyInt(), anyBoolean())
        ;

    }

    @Test
    void postWithTooLongTrajectoryName_should_returnBadRequest() throws Exception {
        // trajectoryToUse > 40 chars
        String trajectoryToUse = "FE_Liv60" + "a".repeat(35) + ".xls";
        String horizon = "2025-2026";

        mockMvc.perform(post("/v1/trajectory/modulation-p2g")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", "1")
                        .param("isCivilYear", "false")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // service should not be called
        verify(p2gFileProcessorService, times(0)).processModulationP2gFile(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void postWithInvalidHorizon_should_returnBadRequest() throws Exception {
        String trajectoryToUse = "FE_Liv60_test.xls";
        String invalidHorizon = "20252026"; // does not match ^\\d{4}-\\d{4}$

        mockMvc.perform(post("/v1/trajectory/dsr-cluster")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", invalidHorizon)
                        .param("studyId", "1")
                        .param("isCivilYear", "false")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(p2gFileProcessorService, times(0)).processModulationP2gFile(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void processDsrCapacityModulationFile_postValidRequest_should_returnCreated_and_callService() throws Exception {
        // Given
        String trajectoryToUse = "CM_test.xls";
        String horizon = "2025-2026";
        String studyId = "1";

        TrajectoryEntity fakeEntity = mock(TrajectoryEntity.class);
        when(p2gFileProcessorService.processCapacityP2gFile(anyString(), anyString(), anyInt(), anyBoolean()))
                .thenReturn(fakeEntity);


        // When / Then
        mockMvc.perform(post("/v1/trajectory/capacity-cost-p2g")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", studyId)
                        .param("isCivilYear", "false")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        verify(p2gFileProcessorService, times(1))
                .processCapacityP2gFile(anyString(), anyString(), anyInt(), anyBoolean())
        ;

    }

    @Test
    void processDsrCapacityModulationFile_postWithTooLongTrajectoryName_should_returnBadRequest() throws Exception {
        // trajectoryToUse > 40 chars
        String trajectoryToUse = "CM_" + "a".repeat(35) + ".xls";
        String horizon = "2025-2026";

        mockMvc.perform(post("/v1/trajectory/capacity-cost-p2g")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // service should not be called
        verify(p2gFileProcessorService, times(0)).processCapacityP2gFile(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void processDsrCapacityModulationFile_postWithInvalidHorizon_should_returnBadRequest() throws Exception {
        String trajectoryToUse = "CM_test.xls";
        String invalidHorizon = "20252026"; // does not match ^\\d{4}-\\d{4}$

        mockMvc.perform(post("/v1/trajectory/capacity-cost-p2g")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", invalidHorizon)
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(p2gFileProcessorService, times(0)).processCapacityP2gFile(anyString(), anyString(), anyInt(), anyBoolean());
    }
}
