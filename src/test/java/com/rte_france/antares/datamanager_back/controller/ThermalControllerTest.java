package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ThermalControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    TrajectoryServiceImpl trajectoryService;

    @BeforeEach
     void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void uploadThermalCapacityTrajectory_mustUploadThermalCapacity() throws Exception {

        when(trajectoryService.processThermalCapacityTrajectory(any(), any(), any(), anyBoolean(),any(),any())).thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory/thermal-capacity")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("area", "FR")
                        .param("trajectoryToUse", "test")
                        .param("technology", "CCGT")
                        .param("studyId", "1")
                        .param("horizon", "2023-2024")
                        .param("isCivilYear", "true")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
    }

    @Test
    void uploadThermalParameterTrajectory_shouldReturnCreatedStatusWhenValidRequest() throws Exception {
        when(trajectoryService.processThermalCommonParameterTrajectory(any(), any(), anyInt()))
                .thenReturn(TrajectoryEntity.builder().build());

        mockMvc.perform(post("/v1/trajectory/thermal-common-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "test")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalParameterTrajectory_shouldReturnBadRequestWhenHorizonIsInvalid() throws Exception {
        mockMvc.perform(post("/v1/trajectory/thermal-common-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "test")
                        .param("horizon", "invalid-horizon")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalParameterTrajectory_shouldReturnBadRequestWhenStudyIdIsMissing() throws Exception {
        mockMvc.perform(post("/v1/trajectory/thermal-common-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "test")
                        .param("horizon", "2023-2024")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andDo(MockMvcResultHandlers.print());
    }


    @Test
    void uploadThermalSpecificParameterTrajectory_shouldReturnCreatedStatusWhenValidRequest() throws Exception {
        when(trajectoryService.processThermalSpecificParameterTrajectory(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(TrajectoryEntity.builder().build());

        mockMvc.perform(post("/v1/trajectory/thermal-specific-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("area", "FR")
                        .param("trajectoryToUse", "specific_param_test")
                        .param("horizon", "2025")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalModulationParameterTrajectory_shouldReturnCreatedStatusWhenValidRequest() throws Exception {
        when(trajectoryService.processThermalModulationParameterTrajectory(any(), any(), anyInt()))
                .thenReturn(TrajectoryEntity.builder().build());

        mockMvc.perform(post("/v1/trajectory/thermal-modulation-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "modulation_test")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalModulationParameterTrajectory_shouldReturnBadRequestWhenHorizonIsMissing() throws Exception {
        mockMvc.perform(post("/v1/trajectory/thermal-modulation-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "modulation_test")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalModulationParameterTrajectory_shouldReturnBadRequestWhenStudyIdIsInvalid() throws Exception {
        mockMvc.perform(post("/v1/trajectory/thermal-modulation-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "modulation_test")
                        .param("horizon", "2023-2024")
                        .param("studyId", "invalid")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalModulationParameterTrajectory_shouldReturnBadRequestWhenTrajectoryToUseIsMissing() throws Exception {
        mockMvc.perform(post("/v1/trajectory/thermal-modulation-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalCostTrajectory_shouldReturnBadRequestWhenTrajectoryToUseIsMissing() throws Exception {
        when(trajectoryService.processThermalEconomicCostTrajectory(any(), any(), anyInt())).thenReturn(TrajectoryEntity.builder().build());
        mockMvc.perform(post("/v1/trajectory/thermal-economic-costs")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "test")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print());
        verify(trajectoryService, times(1)).processThermalEconomicCostTrajectory(any(), any(), anyInt());
    }

    @Test
    void uploadThermalEconomicParamTrajectory_shouldReturnCreatedStatusWhenValidRequest() throws Exception {
        when(trajectoryService.processThermalEconomicParameterTrajectory(any(), any(), anyInt()))
                .thenReturn(TrajectoryEntity.builder().build());

        mockMvc.perform(post("/v1/trajectory/thermal-economic-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "economic_test")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalEconomicParamTrajectory_shouldReturnBadRequestWhenHorizonIsMissing() throws Exception {
        mockMvc.perform(post("/v1/trajectory/thermal-economic-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "economic_test")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalEconomicParamTrajectory_shouldReturnBadRequestWhenStudyIdIsInvalid() throws Exception {
        mockMvc.perform(post("/v1/trajectory/thermal-economic-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "economic_test")
                        .param("horizon", "2023-2024")
                        .param("studyId", "invalid")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andDo(MockMvcResultHandlers.print());
    }

    @Test
    void uploadThermalEconomicParamTrajectory_shouldReturnBadRequestWhenTrajectoryToUseIsMissing() throws Exception {
        mockMvc.perform(post("/v1/trajectory/thermal-economic-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andDo(MockMvcResultHandlers.print());
    }

}



