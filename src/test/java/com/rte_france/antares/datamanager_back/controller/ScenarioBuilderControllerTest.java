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
        when(scenarioBuilderFileProcessorService.processScenarioBuilderFile("scenario_builder_BP23_A_ref_vdef", "2023-2024", 1, "FR"))
                .thenReturn(TrajectoryEntity.builder().id(1).fileName("BP23_A_ref_vdef").type("SCENARIO_BUILDER").build());

        mockMvc.perform(post("/v1/trajectory/scenarioBuilder")
                        .param("trajectoryToUse", "scenario_builder_BP23_A_ref_vdef")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .param("area", "FR"))
                .andExpect(status().isCreated());
    }

    @Test
    void testUploadScenarioBuilderTrajectoryInvalidHorizon() throws Exception {
        mockMvc.perform(post("/v1/trajectory/scenarioBuilder")
                        .param("trajectoryToUse", "scenario_builder_BP23_A_ref_vdef")
                        .param("horizon", "2023")
                        .param("studyId", "1")
                        .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUploadScenarioBuilderTrajectoryInvalidArea() throws Exception {
        mockMvc.perform(post("/v1/trajectory/scenarioBuilder")
                        .param("trajectoryToUse", "scenario_builder_BP23_A_ref_vdef")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .param("area", "invalid@area"))
                .andExpect(status().isBadRequest());
    }
}
