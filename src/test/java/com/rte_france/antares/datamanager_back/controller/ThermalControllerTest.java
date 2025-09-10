package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.TrajectoryService;
import com.rte_france.antares.datamanager_back.service.impl.TrajectoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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


import static org.mockito.ArgumentMatchers.any;
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
    void uploadThermalCommonParametersTrjactoryTest() throws Exception {

        when(trajectoryService.processThermalCommonParameterTrajectory(any(), any(), any())).thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory/thermal-common-parameter")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "common_parameter_test")
                        .param("studyId", "1")
                        .param("horizon", "2023-2024")
                        .accept(MediaType.APPLICATION_JSON_VALUE))

                //Then
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
    }
}