package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.scenario_builder.ScenarioBuilderFileProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScenarioBuilderControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    private ScenarioBuilderFileProcessorService scenarioBuilderFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void testUploadScenarioBuilderTrajectorySuccess() throws Exception {
        when(scenarioBuilderFileProcessorService.processScenarioBuilderFile("scenario_builder_BP23_A_ref_vdef", "2023-2024", 1))
                .thenReturn(TrajectoryEntity.builder().id(1).fileName("BP23_A_ref_vdef").type("SCENARIO_BUILDER").build());

        mockMvc.perform(post("/v1/trajectory/scenarioBuilder")
                        .param("trajectoryToUse", "scenario_builder_BP23_A_ref_vdef")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1"))
                .andExpect(status().isCreated());
    }

    @Test
    void testUploadScenarioBuilderTrajectoryInvalidHorizon() throws Exception {
        mockMvc.perform(post("/v1/trajectory/scenarioBuilder")
                        .param("trajectoryToUse", "scenario_builder_BP23_A_ref_vdef")
                        .param("horizon", "2023")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUploadScenarioBuilderTrajectoryNameTooLong() throws Exception {
        String longTrajectory = "scenario_builder_this_trajectory_name_is_way_too_long_exceeding_forty_characters";

        mockMvc.perform(post("/v1/trajectory/scenarioBuilder")
                        .param("trajectoryToUse", longTrajectory)
                        .param("horizon", "2023-2024")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.antaresErrorMessage")
                        .value("Trajectory name cannot exceed 40 characters"))
                .andExpect(jsonPath("$.type").value("BUSINESS"));
    }

    @Test
    void testUploadScenarioBuilderTrajectoryOnlySpaces() throws Exception {
        mockMvc.perform(post("/v1/trajectory/scenarioBuilder")
                        .param("trajectoryToUse", "   ")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.antaresErrorMessage")
                        .value("The name of the trajectory cannot contain only spaces"))
                .andExpect(jsonPath("$.type").value("BUSINESS"));
    }



}
