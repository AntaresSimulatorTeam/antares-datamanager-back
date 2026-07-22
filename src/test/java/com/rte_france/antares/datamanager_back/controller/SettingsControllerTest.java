package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.settings.SettingsImportService;
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
class SettingsControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    SettingsImportService settingsImportService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }


    @Test
    void testImportTrajectorySettingsSuccess() throws Exception {
        when(settingsImportService.importSettings("BP23_A_ref_200MC", "2028-2029", 1, "FR"))
                .thenReturn(TrajectoryEntity.builder().build()); // Mock the service method to return null or a valid object as needed
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
