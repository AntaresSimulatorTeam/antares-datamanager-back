package com.rte_france.antares.datamanager_back.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testImportTrajectorySettingsSuccess() throws Exception {
        mockMvc.perform(post("/v1/trajectory/settings")
                        .param("trajectoryToUse", "BP23_A_ref_200MC")
                        .param("horizon", "2028-2029")
                        .param("studyId", "1")
                        .param("area", "FR"))
                .andExpect(status().isCreated());
    }

    @Test
    void testImportTrajectorySettingsInvalidTrajectoryName() throws Exception {
        mockMvc.perform(post("/v1/trajectory/settings")
                        .param("trajectoryToUse", "invalid@name")
                        .param("horizon", "2028-2029")
                        .param("studyId", "1")
                        .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testImportTrajectorySettingsInvalidHorizon() throws Exception {
        mockMvc.perform(post("/v1/trajectory/settings")
                        .param("trajectoryToUse", "BP23_A_ref_200MC")
                        .param("horizon", "2028")
                        .param("studyId", "1")
                        .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testImportTrajectorySettingsInvalidArea() throws Exception {
        mockMvc.perform(post("/v1/trajectory/settings")
                        .param("trajectoryToUse", "BP23_A_ref_200MC")
                        .param("horizon", "2028-2029")
                        .param("studyId", "1")
                        .param("area", "invalid@area"))
                .andExpect(status().isBadRequest());
    }
}
