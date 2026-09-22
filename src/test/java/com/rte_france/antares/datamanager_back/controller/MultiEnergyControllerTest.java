package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.HydroMeFileProcessorService;
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
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiEnergyControllerTest {
    private static final String TRAJECTORY_NAME = "hydro_me_test";
    private static final String HORIZON = "2020-2021";
    private static final Integer STUDY_ID = 1;

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    TrajectoryServiceImpl trajectoryServiceImpl;

    @MockBean
    private HydroMeFileProcessorService hydroMeFileProcessorService;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    // ==================== Load-ME Tests ====================

    @Test
    void uploadLoadMeTrajectory_returnsCreatedTrajectory() throws Exception {
        when(trajectoryServiceImpl.processLoadMeTrajectory(any(), any(), any()))
                .thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory/load-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        Mockito.verify(trajectoryServiceImpl, Mockito.times(1))
                .processLoadMeTrajectory(any(), any(), any());
    }

    @Test
    void uploadLoadMeTrajectory_returnsBadRequestForInvalidHorizon() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/load-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "invalid-horizon")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadLoadMeTrajectory_returnsBadRequestForMissingParams() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/load-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());
    }

    // ==================== Constraint-ME Tests ====================

    @Test
    void uploadConstraintMeTrajectory_returnsCreatedTrajectory() throws Exception {
        when(trajectoryServiceImpl.processConstraintMeTrajectory(any(), any(), any()))
                .thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory/constraint-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        Mockito.verify(trajectoryServiceImpl, Mockito.times(1))
                .processConstraintMeTrajectory(any(), any(), any());
    }

    @Test
    void uploadConstraintMeTrajectory_returnsBadRequestForInvalidHorizon() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/constraint-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "invalid-horizon")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadConstraintMeTrajectory_returnsBadRequestForMissingParams() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/constraint-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadConstraintMeTrajectory_withSpacesInFileName_returnsCreatedTrajectory() throws Exception {
        when(trajectoryServiceImpl.processConstraintMeTrajectory(any(), any(), any()))
                .thenReturn(TrajectoryEntity.builder().build());

        this.mockMvc.perform(post("/v1/trajectory/constraint-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "test file with spaces")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        Mockito.verify(trajectoryServiceImpl, Mockito.times(1))
                .processConstraintMeTrajectory(any(), any(), any());
    }

    // ==================== Efficiency-ME Tests ====================

    @Test
    void uploadEfficiencyMeTrajectory_returnsCreatedTrajectory() throws Exception {
        TrajectoryEntity trajectoryEntity = TrajectoryEntity.builder()
                .id(1)
                .fileName("test_efficiency")
                .type(TrajectoryType.EFFICIENCY_ME.name())
                .horizon("2023-2024")
                .version(1)
                .build();

        when(trajectoryServiceImpl.processEfficiencyMeTrajectory("test_efficiency", "2023-2024", 1))
                .thenReturn(trajectoryEntity);

        this.mockMvc.perform(post("/v1/trajectory/efficiency-me")
                        .param("trajectoryToUse", "test_efficiency")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trajectoryName").value("test_efficiency"))
                .andExpect(jsonPath("$.type").value(TrajectoryType.EFFICIENCY_ME.name()));

        verify(trajectoryServiceImpl, times(1)).processEfficiencyMeTrajectory("test_efficiency", "2023-2024", 1);
    }

    // ==================== Hydro-Capacity-ME Tests ====================

    @Test
    void uploadHydroCapacityMeTrajectory_returns201_andCallsService() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(123);
        entity.setFileName(TRAJECTORY_NAME);
        entity.setType(TrajectoryType.HYDRO_CAPACITY_ME.name());
        entity.setVersion(1);
        entity.setHorizon(HORIZON);

        when(hydroMeFileProcessorService.processHydroCapacityMeFile(
                TRAJECTORY_NAME,
                HORIZON,
                STUDY_ID
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/hydro-capacity-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", TRAJECTORY_NAME)
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.trajectoryName").value(TRAJECTORY_NAME))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.horizon").value(HORIZON));

        verify(hydroMeFileProcessorService, times(1))
                .processHydroCapacityMeFile(TRAJECTORY_NAME, HORIZON, STUDY_ID);
        verifyNoMoreInteractions(hydroMeFileProcessorService);
    }

    @Test
    void uploadHydroCapacityMeTrajectory_whenTrajectoryNameTooLong_returns400_andDoesNotCallService() throws Exception {
        String tooLongName = "x".repeat(41);

        mockMvc.perform(post("/v1/trajectory/hydro-capacity-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", tooLongName)
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroMeFileProcessorService);
    }

    @Test
    void uploadHydroCapacityMeTrajectory_whenHorizonInvalid_returns400_andDoesNotCallService() throws Exception {
        String invalidHorizon = "2020-21";

        mockMvc.perform(post("/v1/trajectory/hydro-capacity-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", TRAJECTORY_NAME)
                        .param("horizon", invalidHorizon)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroMeFileProcessorService);
    }

    @Test
    void uploadHydroCapacityMeTrajectory_whenHorizonMissingSecondPart_returns400_andDoesNotCallService() throws Exception {
        String invalidHorizon = "2020";

        mockMvc.perform(post("/v1/trajectory/hydro-capacity-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", TRAJECTORY_NAME)
                        .param("horizon", invalidHorizon)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroMeFileProcessorService);
    }

    @Test
    void uploadHydroCapacityMeTrajectory_whenStudyIdNotProvided_returns400_andDoesNotCallService() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-capacity-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", TRAJECTORY_NAME)
                        .param("horizon", HORIZON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroMeFileProcessorService);
    }

    @Test
    void uploadHydroCapacityMeTrajectory_whenTrajectoryNameNotProvided_returns400_andDoesNotCallService() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-capacity-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroMeFileProcessorService);
    }

    @Test
    void uploadHydroCapacityMeTrajectory_withValidHorizonFormats_returns201() throws Exception {
        String[] validHorizons = {"2020-2021", "2025-2026", "2030-2031"};

        for (String horizon : validHorizons) {
            TrajectoryEntity entity = new TrajectoryEntity();
            entity.setId(123);
            entity.setFileName(TRAJECTORY_NAME);
            entity.setType(TrajectoryType.HYDRO_CAPACITY_ME.name());
            entity.setVersion(1);
            entity.setHorizon(horizon);

            when(hydroMeFileProcessorService.processHydroCapacityMeFile(
                    TRAJECTORY_NAME,
                    horizon,
                    STUDY_ID
            )).thenReturn(entity);

            mockMvc.perform(post("/v1/trajectory/hydro-capacity-me")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("trajectoryToUse", TRAJECTORY_NAME)
                            .param("horizon", horizon)
                            .param("studyId", STUDY_ID.toString()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.horizon").value(horizon));

            verify(hydroMeFileProcessorService, times(1))
                    .processHydroCapacityMeFile(TRAJECTORY_NAME, horizon, STUDY_ID);
        }
    }

    @Test
    void uploadHydroCapacityMeTrajectory_withMultipleStudyIds_returns201() throws Exception {
        Integer[] studyIds = {1, 42, 999};

        for (Integer studyId : studyIds) {
            TrajectoryEntity entity = new TrajectoryEntity();
            entity.setId(studyId * 100);
            entity.setFileName(TRAJECTORY_NAME);
            entity.setType(TrajectoryType.HYDRO_CAPACITY_ME.name());
            entity.setVersion(1);
            entity.setHorizon(HORIZON);

            when(hydroMeFileProcessorService.processHydroCapacityMeFile(
                    TRAJECTORY_NAME,
                    HORIZON,
                    studyId
            )).thenReturn(entity);

            mockMvc.perform(post("/v1/trajectory/hydro-capacity-me")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("trajectoryToUse", TRAJECTORY_NAME)
                            .param("horizon", HORIZON)
                            .param("studyId", studyId.toString()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(studyId * 100));

            verify(hydroMeFileProcessorService, times(1))
                    .processHydroCapacityMeFile(TRAJECTORY_NAME, HORIZON, studyId);
        }
    }
}
