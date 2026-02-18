package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.service.dsr.DsrCapacityModulationFileProcessorService;
import com.rte_france.antares.datamanager_back.service.dsr.DsrFileProcessorService;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DsrControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    private DsrFileProcessorService dsrFileProcessorService;

    @MockBean
    private DsrCapacityModulationFileProcessorService dsrCapacityModulationFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void postValidRequest_should_returnCreated_and_callService() throws Exception {
        // Given
        String area = "FR";
        String trajectoryToUse = "cluster_DSR_test.xls";
        String horizon = "2025-2026";
        String studyId = "1";
        String isCivilYear = "true";

        TrajectoryEntity fakeEntity = Mockito.mock(TrajectoryEntity.class);
        when(dsrFileProcessorService.processDsrClusterFile(anyString(), anyString(), anyInt(), anyBoolean(), anyString()))
                .thenReturn(fakeEntity);


        // When / Then
        mockMvc.perform(post("/v1/trajectory/dsr-cluster")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("area", area)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", studyId)
                        .param("isCivilYear", isCivilYear)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        verify(dsrFileProcessorService, times(1))
                .processDsrClusterFile(anyString(), anyString(), anyInt(), anyBoolean(), anyString())
        ;

    }

    @Test
    void postWithTooLongTrajectoryName_should_returnBadRequest() throws Exception {
        String area = "FR";
        // trajectoryToUse > 40 chars
        String trajectoryToUse = "cluster_DSR_" + "a".repeat(35) + ".xls";
        String horizon = "2025-2026";

        mockMvc.perform(post("/v1/trajectory/dsr-cluster")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("area", area)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", "1")
                        .param("isCivilYear", "false")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // service should not be called
        verify(dsrFileProcessorService, times(0)).processDsrClusterFile(anyString(), anyString(), anyInt(), anyBoolean(), anyString());
    }

    @Test
    void postWithInvalidHorizon_should_returnBadRequest() throws Exception {
        String area = "FR";
        String trajectoryToUse = "cluster_DSR_test.xls";
        String invalidHorizon = "20252026"; // does not match ^\\d{4}-\\d{4}$

        mockMvc.perform(post("/v1/trajectory/dsr-cluster")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("area", area)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", invalidHorizon)
                        .param("studyId", "1")
                        .param("isCivilYear", "false")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(dsrFileProcessorService, times(0)).processDsrClusterFile(anyString(), anyString(), anyInt(), anyBoolean(), anyString());
    }

    @Test
    void processDsrCapacityModulationFile_postValidRequest_should_returnCreated_and_callService() throws Exception {
        // Given
        String trajectoryToUse = "CM_test.xls";
        String horizon = "2025-2026";
        String studyId = "1";

        TrajectoryEntity fakeEntity = Mockito.mock(TrajectoryEntity.class);
        when(dsrCapacityModulationFileProcessorService.processDsrCapacityModulationFile(anyString(), anyString(), anyInt()))
                .thenReturn(fakeEntity);


        // When / Then
        mockMvc.perform(post("/v1/trajectory/dsr-capacity-modulation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", studyId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        verify(dsrCapacityModulationFileProcessorService, times(1))
                .processDsrCapacityModulationFile(anyString(), anyString(), anyInt())
        ;

    }

    @Test
    void processDsrCapacityModulationFile_postWithTooLongTrajectoryName_should_returnBadRequest() throws Exception {
        // trajectoryToUse > 40 chars
        String trajectoryToUse = "CM_" + "a".repeat(35) + ".xls";
        String horizon = "2025-2026";

        mockMvc.perform(post("/v1/trajectory/dsr-capacity-modulation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // service should not be called
        verify(dsrCapacityModulationFileProcessorService, times(0)).processDsrCapacityModulationFile(anyString(), anyString(), anyInt());
    }

    @Test
    void processDsrCapacityModulationFile_postWithInvalidHorizon_should_returnBadRequest() throws Exception {
        String trajectoryToUse = "CM_test.xls";
        String invalidHorizon = "20252026"; // does not match ^\\d{4}-\\d{4}$

        mockMvc.perform(post("/v1/trajectory/dsr-capacity-modulation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", invalidHorizon)
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(dsrCapacityModulationFileProcessorService, times(0)).processDsrCapacityModulationFile(anyString(), anyString(), anyInt());
    }
}
