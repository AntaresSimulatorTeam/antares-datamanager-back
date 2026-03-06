package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.misc.MiscFileProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MiscControllerTest {

    @Autowired
    protected WebApplicationContext wac;
    
    private MockMvc mockMvc;

    @MockBean
    private MiscFileProcessorService miscFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void uploadInstalledMiscTrajectory_returns201_andCallsService() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(123);
        entity.setFileName("installedMisc_test");
        entity.setType("MISC_CAPACITY");
        entity.setVersion(1);
        entity.setArea("FR");
        entity.setHasTimeSeries(false);

        when(miscFileProcessorService.processInstalledMiscFile(
                "installedMisc_test",
                "2029-2030",
                1,
                "FR",
                false
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/installed-misc")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("trajectoryToUse", "installedMisc_test")
                        .param("horizon", "2029-2030")
                        .param("studyId", "1")
                        .param("isCivilYear", "false"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.trajectoryName").value("installedMisc_test"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.area").value("FR"));

        verify(miscFileProcessorService, times(1))
                .processInstalledMiscFile("installedMisc_test", "2029-2030", 1, "FR", false);
        verifyNoMoreInteractions(miscFileProcessorService);
    }

    @Test
    void uploadInstalledMiscTrajectory_whenTrajectoryNameTooLong_returns400_andDoesNotCallService() throws Exception {
        String tooLong = "x".repeat(41);

        mockMvc.perform(post("/v1/trajectory/installed-misc")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("trajectoryToUse", tooLong)
                        .param("horizon", "2029-2030")
                        .param("studyId", "1")
                        .param("isCivilYear", "false"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(miscFileProcessorService);
    }

    @Test
    void uploadInstalledMiscTrajectory_whenHorizonInvalid_returns400_andDoesNotCallService() throws Exception {
        mockMvc.perform(post("/v1/trajectory/installed-misc")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("trajectoryToUse", "installedMisc_test")
                        .param("horizon", "2030") // invalide: attendu "YYYY-YYYY"
                        .param("studyId", "1")
                        .param("isCivilYear", "false"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(miscFileProcessorService);
    }

    @Test
    void uploadLoadFactorMiscTrajectory_returns201_andCallsService() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(456);
        entity.setFileName("loadFactor_test");
        entity.setType("MISC_LOAD");
        entity.setVersion(1);
        entity.setArea("FR");
        entity.setHasTimeSeries(true);

        when(miscFileProcessorService.processLoadFactorMiscFile(
                "loadFactor_test",
                "2029-2030",
                1,
                "FR"
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/load-factor-misc")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("trajectoryToUse", "loadFactor_test")
                        .param("horizon", "2029-2030")
                        .param("studyId", "1"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(456))
                .andExpect(jsonPath("$.trajectoryName").value("loadFactor_test"))
                .andExpect(jsonPath("$.hasTimeSeries").value(true));

        verify(miscFileProcessorService, times(1))
                .processLoadFactorMiscFile("loadFactor_test", "2029-2030", 1, "FR");
        verifyNoMoreInteractions(miscFileProcessorService);
    }

    @Test
    void uploadLoadFactorMiscTrajectory_whenHorizonInvalid_returns400_andDoesNotCallService() throws Exception {
        mockMvc.perform(post("/v1/trajectory/load-factor-misc")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", "FR")
                        .param("trajectoryToUse", "loadFactor_test")
                        .param("horizon", "bad-horizon")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(miscFileProcessorService);
    }
}