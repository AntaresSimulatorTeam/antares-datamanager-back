package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.hydro.HydroFileProcessorService;
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

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HydroControllerTest {
    private static final String AREA_FR = "FR";
    private static final String HORIZON = "2029-2030";
    private static final String TRAJ = "BP_23";
    
    @Autowired
    protected WebApplicationContext wac;

    private MockMvc mockMvc;

    @MockBean
    private HydroFileProcessorService hydroFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void uploadSeriesHydroTrajectory_returns201_andCallsService() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(123);
        entity.setFileName(TRAJ);
        entity.setType("HYDRO_SERIES");
        entity.setVersion(1);
        entity.setArea(AREA_FR);
        entity.setHasTimeSeries(false);

        when(hydroFileProcessorService.processHydroSeriesFile(
                TRAJ,
                HORIZON,
                1,
                AREA_FR,
                false
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/hydro-series")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", AREA_FR)
                        .param("trajectoryToUse", TRAJ)
                        .param("horizon", HORIZON)
                        .param("studyId", "1")
                        .param("isCivilYear", "false"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.trajectoryName").value(TRAJ))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.area").value(AREA_FR));

        verify(hydroFileProcessorService, times(1))
                .processHydroSeriesFile(TRAJ, HORIZON, 1, AREA_FR, false);
        verifyNoMoreInteractions(hydroFileProcessorService);
    }

    @Test
    void uploadSeriesHydroTrajectory_whenTrajectoryNameTooLong_returns400_andDoesNotCallService() throws Exception {
        String tooLong = "x".repeat(41);

        mockMvc.perform(post("/v1/trajectory/hydro-series")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", AREA_FR)
                        .param("trajectoryToUse", tooLong)
                        .param("horizon", HORIZON)
                        .param("studyId", "1")
                        .param("isCivilYear", "false"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroFileProcessorService);
    }

    @Test
    void uploadSeriesHydroTrajectory_whenHorizonInvalid_returns400_andDoesNotCallService() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-series")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("area", AREA_FR)
                        .param("trajectoryToUse", TRAJ)
                        .param("horizon", "2030")
                        .param("studyId", "1")
                        .param("isCivilYear", "false"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroFileProcessorService);
    }
}
