package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.hydro.HydroMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroParametersMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroTimeSeriesMeFileProcessorService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
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
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentMatchers;
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

    @MockBean
    private HydroParametersMeFileProcessorService hydroParametersMeFileProcessorService;

    @MockBean
    private HydroTimeSeriesMeFileProcessorService hydroTimeSeriesMeFileProcessorService;

    @MockBean
    private PathSecurityUtil pathSecurityUtil;

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

    @Test
    void uploadHydroCapacityMeTrajectory_returns201WithCorrectResponse() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(456);
        entity.setFileName("hydro_capacity_test");
        entity.setType(TrajectoryType.HYDRO_CAPACITY_ME.name());
        entity.setVersion(2);
        entity.setHorizon("2021-2022");

        when(hydroMeFileProcessorService.processHydroCapacityMeFile(
                "hydro_capacity_test",
                "2021-2022",
                99
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/hydro-capacity-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "hydro_capacity_test")
                        .param("horizon", "2021-2022")
                        .param("studyId", "99"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(456))
                .andExpect(jsonPath("$.trajectoryName").value("hydro_capacity_test"))
                .andExpect(jsonPath("$.type").value(TrajectoryType.HYDRO_CAPACITY_ME.name()))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.horizon").value("2021-2022"));

        verify(hydroMeFileProcessorService, times(1))
                .processHydroCapacityMeFile("hydro_capacity_test", "2021-2022", 99);
        }

        // ==================== Hydro-Parameters-ME Tests ====================

        @Test
        void uploadHydroParametersMeTrajectory_returns201AndCallsPathSecurityUtilAndService() throws Exception {
            TrajectoryEntity entity = new TrajectoryEntity();
            entity.setId(789);
            entity.setFileName("hydro_params_test");
            entity.setType(TrajectoryType.HYDRO_PARAMETERS_ME.name());
            entity.setVersion(1);
            entity.setHorizon(HORIZON);

            when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory(
                    TRAJECTORY_NAME,
                    HORIZON,
                    STUDY_ID
            )).thenReturn(entity);

            mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("trajectoryToUse", TRAJECTORY_NAME)
                            .param("horizon", HORIZON)
                            .param("studyId", STUDY_ID.toString()))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(789))
                    .andExpect(jsonPath("$.trajectoryName").value("hydro_params_test"))
                    .andExpect(jsonPath("$.type").value(TrajectoryType.HYDRO_PARAMETERS_ME.name()))
                    .andExpect(jsonPath("$.horizon").value(HORIZON));

            verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.any(java.util.function.Function.class), eq(TRAJECTORY_NAME));
            verify(hydroParametersMeFileProcessorService, times(1))
                    .processHydroParametersMeDirectory(TRAJECTORY_NAME, HORIZON, STUDY_ID);
        }

        @Test
        void uploadHydroParametersMeTrajectory_callsPathSecurityUtilWithCorrectParameters() throws Exception {
            TrajectoryEntity entity = new TrajectoryEntity();
            entity.setId(999);
            entity.setFileName("params_trajectory");
            entity.setType(TrajectoryType.HYDRO_PARAMETERS_ME.name());
            entity.setVersion(1);
            entity.setHorizon("2022-2023");

            when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory(
                    "params_trajectory",
                    "2022-2023",
                    50
            )).thenReturn(entity);

            mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("trajectoryToUse", "params_trajectory")
                            .param("horizon", "2022-2023")
                            .param("studyId", "50"))
                    .andExpect(status().isCreated());

            verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.any(java.util.function.Function.class), eq("params_trajectory"));
        }

         @Test
         void uploadHydroParametersMeTrajectory_whenTrajectoryNameTooLong_doesNotCallPathSecurityUtil() throws Exception {
             String tooLongName = "x".repeat(41);

             mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                             .param("trajectoryToUse", tooLongName)
                             .param("horizon", HORIZON)
                             .param("studyId", STUDY_ID.toString()))
                     .andExpect(status().isBadRequest());

             verifyNoInteractions(pathSecurityUtil);
             verifyNoInteractions(hydroParametersMeFileProcessorService);
         }

         // ==================== Hydro-TS-ME Tests ====================

         @Test
         void uploadHydroTimeSeriesMeTrajectory_returns201AndCallsPathSecurityUtilAndService() throws Exception {
             TrajectoryEntity entity = new TrajectoryEntity();
             entity.setId(456);
             entity.setFileName("hydro_ts_test");
             entity.setType(TrajectoryType.HYDRO_TIME_SERIES_ME.name());
             entity.setVersion(1);
             entity.setHorizon(HORIZON);

             when(hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(
                     "hydro_ts_test",
                     HORIZON,
                     STUDY_ID
             )).thenReturn(entity);

             mockMvc.perform(post("/v1/trajectory/hydro-ts-me")
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                             .param("trajectoryToUse", "hydro_ts_test")
                             .param("horizon", HORIZON)
                             .param("studyId", STUDY_ID.toString()))
                     .andExpect(status().isCreated())
                     .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                     .andExpect(jsonPath("$.id").value(456))
                     .andExpect(jsonPath("$.trajectoryName").value("hydro_ts_test"))
                     .andExpect(jsonPath("$.type").value(TrajectoryType.HYDRO_TIME_SERIES_ME.name()))
                     .andExpect(jsonPath("$.version").value(1))
                     .andExpect(jsonPath("$.horizon").value(HORIZON));

             verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.any(java.util.function.Function.class), eq("hydro_ts_test"));
             verify(hydroTimeSeriesMeFileProcessorService, times(1))
                     .processHydroTimeSeriesMeDirectory("hydro_ts_test", HORIZON, STUDY_ID);
         }

         @Test
         void uploadHydroTimeSeriesMeTrajectory_whenTrajectoryNameTooLong_returns400AndDoesNotCallService() throws Exception {
             String tooLongName = "x".repeat(41);

             mockMvc.perform(post("/v1/trajectory/hydro-ts-me")
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                             .param("trajectoryToUse", tooLongName)
                             .param("horizon", HORIZON)
                             .param("studyId", STUDY_ID.toString()))
                     .andExpect(status().isBadRequest());

             verifyNoInteractions(hydroTimeSeriesMeFileProcessorService);
         }

         @Test
         void uploadHydroTimeSeriesMeTrajectory_whenHorizonInvalid_returns400AndDoesNotCallService() throws Exception {
             String invalidHorizon = "2020-21";

             mockMvc.perform(post("/v1/trajectory/hydro-ts-me")
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                             .param("trajectoryToUse", TRAJECTORY_NAME)
                             .param("horizon", invalidHorizon)
                             .param("studyId", STUDY_ID.toString()))
                     .andExpect(status().isBadRequest());

             verifyNoInteractions(hydroTimeSeriesMeFileProcessorService);
         }

         @Test
         void uploadHydroTimeSeriesMeTrajectory_whenStudyIdMissing_returns400AndDoesNotCallService() throws Exception {
             mockMvc.perform(post("/v1/trajectory/hydro-ts-me")
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                             .param("trajectoryToUse", TRAJECTORY_NAME)
                             .param("horizon", HORIZON))
                     .andExpect(status().isBadRequest());

             verifyNoInteractions(hydroTimeSeriesMeFileProcessorService);
         }

         @Test
         void uploadHydroTimeSeriesMeTrajectory_whenTrajectoryToUseMissing_returns400AndDoesNotCallService() throws Exception {
             mockMvc.perform(post("/v1/trajectory/hydro-ts-me")
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                             .param("horizon", HORIZON)
                             .param("studyId", STUDY_ID.toString()))
                     .andExpect(status().isBadRequest());

             verifyNoInteractions(hydroTimeSeriesMeFileProcessorService);
         }

         @Test
         void uploadHydroTimeSeriesMeTrajectory_withValidHorizonFormats_returns201() throws Exception {
             String[] validHorizons = {"2020-2021", "2025-2026", "2030-2031"};

             for (String horizon : validHorizons) {
                 TrajectoryEntity entity = new TrajectoryEntity();
                 entity.setId(123);
                 entity.setFileName(TRAJECTORY_NAME);
                 entity.setType(TrajectoryType.HYDRO_TIME_SERIES_ME.name());
                 entity.setVersion(1);
                 entity.setHorizon(horizon);

                 when(hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(
                         TRAJECTORY_NAME,
                         horizon,
                         STUDY_ID
                 )).thenReturn(entity);

                 mockMvc.perform(post("/v1/trajectory/hydro-ts-me")
                                 .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                 .param("trajectoryToUse", TRAJECTORY_NAME)
                                 .param("horizon", horizon)
                                 .param("studyId", STUDY_ID.toString()))
                         .andExpect(status().isCreated())
                         .andExpect(jsonPath("$.horizon").value(horizon));

                 verify(hydroTimeSeriesMeFileProcessorService, times(1))
                         .processHydroTimeSeriesMeDirectory(TRAJECTORY_NAME, horizon, STUDY_ID);
             }
         }

         @Test
         void uploadHydroTimeSeriesMeTrajectory_callsPathSecurityUtilWithCorrectParameters() throws Exception {
             TrajectoryEntity entity = new TrajectoryEntity();
             entity.setId(789);
             entity.setFileName("hydro_ts_trajectory");
             entity.setType(TrajectoryType.HYDRO_TIME_SERIES_ME.name());
             entity.setVersion(1);
             entity.setHorizon("2022-2023");

             when(hydroTimeSeriesMeFileProcessorService.processHydroTimeSeriesMeDirectory(
                     "hydro_ts_trajectory",
                     "2022-2023",
                     50
             )).thenReturn(entity);

             mockMvc.perform(post("/v1/trajectory/hydro-ts-me")
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                             .param("trajectoryToUse", "hydro_ts_trajectory")
                             .param("horizon", "2022-2023")
                             .param("studyId", "50"))
                     .andExpect(status().isCreated());

             verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.any(java.util.function.Function.class), eq("hydro_ts_trajectory"));
         }
 }
