package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.configuration.AntaresDataManagerProperties;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.constraint_me.ConstraintMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.efficiency_me.EfficiencyMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroParametersMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroReservoirLevelsMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.hydro.HydroTimeSeriesMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.thermal_me.ThermalMeFileProcessorService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import org.mockito.ArgumentMatchers;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiEnergyControllerTest {
    private static final String TRAJECTORY_NAME = "hydro_me_test";
    private static final String HORIZON = "2020-2021";
    private static final Integer STUDY_ID = 1;

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    private TrajectoryServiceImpl trajectoryServiceImpl;

    @MockBean
    private HydroMeFileProcessorService hydroMeFileProcessorService;

    @MockBean
    private HydroParametersMeFileProcessorService hydroParametersMeFileProcessorService;

    @MockBean
    private HydroTimeSeriesMeFileProcessorService hydroTimeSeriesMeFileProcessorService;

    @MockBean
    private ThermalMeFileProcessorService thermalMeFileProcessorService;

    @MockBean
    private HydroReservoirLevelsMeFileProcessorService hydroReservoirLevelsMeFileProcessorService;

    @MockBean
    private ConstraintMeFileProcessorService constraintMeFileProcessorService;

    @MockBean
    private EfficiencyMeFileProcessorService efficiencyMeFileProcessorService;

    @MockBean
    private PathSecurityUtil pathSecurityUtil;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    // ==================== Load-ME Tests ====================

    @Test
    void uploadLoadMeTrajectory_returnsCreatedTrajectory() throws Exception {
        when(trajectoryServiceImpl.processLoadMeTrajectory(any(), any(), any()))
                .thenReturn(TrajectoryEntity.builder().build());

        executePathSecurityLambda();

        this.mockMvc.perform(post("/v1/trajectory/load-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(trajectoryServiceImpl, times(1))
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

    @ParameterizedTest
    @ValueSource(strings = {"2020", "2020-21"})
    void uploadHydroCapacityMeTrajectory_whenHorizonInvalidOrWhenHorizonMissingSecondPart_returns400_andDoesNotCallService(String invalidHorizon) throws Exception {
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

        executePathSecurityLambda();

        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "params_trajectory")
                        .param("horizon", "2022-2023")
                        .param("studyId", "50"))
                .andExpect(status().isCreated());

        verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.<Function<AntaresDataManagerProperties, Path>>any(), eq("params_trajectory"));
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

        executePathSecurityLambda();

        mockMvc.perform(post("/v1/trajectory/hydro-ts-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "hydro_ts_trajectory")
                        .param("horizon", "2022-2023")
                        .param("studyId", "50"))
                .andExpect(status().isCreated());

        verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.any(java.util.function.Function.class), eq("hydro_ts_trajectory"));
    }

    // ==================== Thermal-ME Tests ====================

    @Test
    void uploadThermalMeTrajectory_returnsCreatedTrajectory() throws Exception {
        when(thermalMeFileProcessorService.processThermalMeFile(anyString(), anyString(), anyInt()))
                .thenReturn(TrajectoryEntity.builder().build());

        executePathSecurityLambda();

        this.mockMvc.perform(post("/v1/trajectory/thermal-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "2023-2024")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isCreated())
                .andDo(MockMvcResultHandlers.print())
                .andReturn();
        verify(thermalMeFileProcessorService, times(1))
                .processThermalMeFile(anyString(), anyString(), anyInt());
    }

    @Test
    void uploadThermalMeTrajectory_returnsBadRequestForInvalidHorizon() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/thermal-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("horizon", "invalid-horizon")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadThermalMeTrajectory_returnsBadRequestForMissingParams() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/thermal-me")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .param("trajectoryToUse", "testTrajectory")
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest());
    }

    // ==================== Hydro-Reservoir-Levels-ME Tests ====================

    @Test
    void uploadHydroReservoirLevelsMeTrajectory_returns201AndCallsPathSecurityUtilAndService() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(789);
        entity.setFileName("hydro_res_test");
        entity.setType(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name());
        entity.setVersion(1);
        entity.setHorizon(HORIZON);

        when(hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile(
                "hydro_res_test",
                HORIZON,
                STUDY_ID
        )).thenReturn(entity);

        mockMvc.perform(post("/v1/trajectory/hydro-reservoir-levels-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "hydro_res_test")
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(789))
                .andExpect(jsonPath("$.trajectoryName").value("hydro_res_test"))
                .andExpect(jsonPath("$.type").value(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.horizon").value(HORIZON));

        verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.any(java.util.function.Function.class), eq("hydro_res_test"));
        verify(hydroReservoirLevelsMeFileProcessorService, times(1))
                .processHydroReservoirLevelsMeFile("hydro_res_test", HORIZON, STUDY_ID);
    }

    @Test
    void uploadHydroReservoirLevelsMeTrajectory_whenTrajectoryNameTooLong_returns400AndDoesNotCallService() throws Exception {
        String tooLongName = "x".repeat(41);

        mockMvc.perform(post("/v1/trajectory/hydro-reservoir-levels-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", tooLongName)
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroReservoirLevelsMeFileProcessorService);
    }

    @Test
    void uploadHydroReservoirLevelsMeTrajectory_whenHorizonInvalid_returns400AndDoesNotCallService() throws Exception {
        String invalidHorizon = "2020-21";

        mockMvc.perform(post("/v1/trajectory/hydro-reservoir-levels-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", TRAJECTORY_NAME)
                        .param("horizon", invalidHorizon)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroReservoirLevelsMeFileProcessorService);
    }

    @Test
    void uploadHydroReservoirLevelsMeTrajectory_whenStudyIdMissing_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-reservoir-levels-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", TRAJECTORY_NAME)
                        .param("horizon", HORIZON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroReservoirLevelsMeFileProcessorService);
    }

    @Test
    void uploadHydroReservoirLevelsMeTrajectory_whenTrajectoryToUseMissing_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-reservoir-levels-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(hydroReservoirLevelsMeFileProcessorService);
    }

    @Test
    void uploadHydroReservoirLevelsMeTrajectory_withValidHorizonFormats_returns201() throws Exception {
        String[] validHorizons = {"2020-2021", "2025-2026", "2030-2031"};

        for (String horizon : validHorizons) {
            TrajectoryEntity entity = new TrajectoryEntity();
            entity.setId(123);
            entity.setFileName(TRAJECTORY_NAME);
            entity.setType(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name());
            entity.setVersion(1);
            entity.setHorizon(horizon);

            when(hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile(
                    TRAJECTORY_NAME,
                    horizon,
                    STUDY_ID
            )).thenReturn(entity);

            mockMvc.perform(post("/v1/trajectory/hydro-reservoir-levels-me")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("trajectoryToUse", TRAJECTORY_NAME)
                            .param("horizon", horizon)
                            .param("studyId", STUDY_ID.toString()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.horizon").value(horizon));

            verify(hydroReservoirLevelsMeFileProcessorService, times(1))
                    .processHydroReservoirLevelsMeFile(TRAJECTORY_NAME, horizon, STUDY_ID);
        }
    }

    @Test
    void uploadHydroReservoirLevelsMeTrajectory_callsPathSecurityUtilWithCorrectParameters() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(999);
        entity.setFileName("hydro_res_trajectory");
        entity.setType(TrajectoryType.HYDRO_RESERVOIR_LEVELS_ME.name());
        entity.setVersion(1);
        entity.setHorizon("2022-2023");

        when(hydroReservoirLevelsMeFileProcessorService.processHydroReservoirLevelsMeFile(
                "hydro_res_trajectory",
                "2022-2023",
                50
        )).thenReturn(entity);

        executePathSecurityLambda();

        mockMvc.perform(post("/v1/trajectory/hydro-reservoir-levels-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "hydro_res_trajectory")
                        .param("horizon", "2022-2023")
                        .param("studyId", "50"))
                .andExpect(status().isCreated());

        verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.any(java.util.function.Function.class), eq("hydro_res_trajectory"));
    }

    // ==================== Constraint-ME Tests ====================

    @Test
    void uploadConstraintMeTrajectory_returnsCreatedTrajectory() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(123);
        entity.setFileName("constraint_me_test");
        entity.setType(TrajectoryType.CONSTRAINT_ME.name());
        entity.setVersion(1);
        entity.setHorizon(HORIZON);

        when(constraintMeFileProcessorService.processConstraintMeFile(
                "constraint_me_test",
                HORIZON,
                STUDY_ID
        )).thenReturn(entity);

        executePathSecurityLambda();

        mockMvc.perform(post("/v1/trajectory/constraint-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "constraint_me_test")
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.trajectoryName").value("constraint_me_test"))
                .andExpect(jsonPath("$.type").value(TrajectoryType.CONSTRAINT_ME.name()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.horizon").value(HORIZON));

        verify(constraintMeFileProcessorService, times(1))
                .processConstraintMeFile("constraint_me_test", HORIZON, STUDY_ID);
        verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.any(java.util.function.Function.class), eq("constraint_me_test"));
    }

    @Test
    void uploadConstraintMeTrajectory_returnsBadRequestForInvalidHorizon() throws Exception {
        mockMvc.perform(post("/v1/trajectory/constraint-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "constraint_me_test")
                        .param("horizon", "invalid-horizon")
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(constraintMeFileProcessorService);
    }

    @Test
    void uploadConstraintMeTrajectory_returnsBadRequestForMissingParams() throws Exception {
        mockMvc.perform(post("/v1/trajectory/constraint-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "constraint_me_test")
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(constraintMeFileProcessorService);
    }

    @Test
    void uploadConstraintMeTrajectory_returnsBadRequestForTooLongTrajectoryName() throws Exception {
        String tooLongName = "x".repeat(41);

        mockMvc.perform(post("/v1/trajectory/constraint-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", tooLongName)
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(constraintMeFileProcessorService);
    }

    // ==================== Efficiency-ME Tests ====================

    @Test
    void uploadEfficiencyMeTrajectory_returnsCreatedTrajectory() throws Exception {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(456);
        entity.setFileName("efficiency_me_test");
        entity.setType(TrajectoryType.EFFICIENCY_ME.name());
        entity.setVersion(1);
        entity.setHorizon(HORIZON);

        when(efficiencyMeFileProcessorService.processEfficiencyMeFile(
                "efficiency_me_test",
                HORIZON,
                STUDY_ID
        )).thenReturn(entity);

        executePathSecurityLambda();

        mockMvc.perform(post("/v1/trajectory/efficiency-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "efficiency_me_test")
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(456))
                .andExpect(jsonPath("$.trajectoryName").value("efficiency_me_test"))
                .andExpect(jsonPath("$.type").value(TrajectoryType.EFFICIENCY_ME.name()))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.horizon").value(HORIZON));

        verify(efficiencyMeFileProcessorService, times(1))
                .processEfficiencyMeFile("efficiency_me_test", HORIZON, STUDY_ID);
        verify(pathSecurityUtil, times(1)).resolveSafePath(ArgumentMatchers.any(java.util.function.Function.class), eq("efficiency_me_test"));
    }

    @Test
    void uploadEfficiencyMeTrajectory_returnsBadRequestForInvalidHorizon() throws Exception {
        mockMvc.perform(post("/v1/trajectory/efficiency-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "efficiency_me_test")
                        .param("horizon", "2020")
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(efficiencyMeFileProcessorService);
    }

    @Test
    void uploadEfficiencyMeTrajectory_returnsBadRequestForMissingParams() throws Exception {
        mockMvc.perform(post("/v1/trajectory/efficiency-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(efficiencyMeFileProcessorService);
    }

    @Test
    void uploadEfficiencyMeTrajectory_returnsBadRequestForTooLongTrajectoryName() throws Exception {
        String tooLongName = "x".repeat(41);

        mockMvc.perform(post("/v1/trajectory/efficiency-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", tooLongName)
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(efficiencyMeFileProcessorService);
    }

    @Test
    void uploadEfficiencyMeTrajectory_withDifferentValidHorizons_returns201() throws Exception {
        String[] validHorizons = {"2020-2021", "2025-2026", "2030-2031"};

        for (String horizon : validHorizons) {
            TrajectoryEntity entity = new TrajectoryEntity();
            entity.setId(456);
            entity.setFileName("efficiency_me_test");
            entity.setType(TrajectoryType.EFFICIENCY_ME.name());
            entity.setVersion(1);
            entity.setHorizon(horizon);

            when(efficiencyMeFileProcessorService.processEfficiencyMeFile(
                    "efficiency_me_test",
                    horizon,
                    STUDY_ID
            )).thenReturn(entity);

            mockMvc.perform(post("/v1/trajectory/efficiency-me")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("trajectoryToUse", "efficiency_me_test")
                            .param("horizon", horizon)
                            .param("studyId", STUDY_ID.toString()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.horizon").value(horizon));

            verify(efficiencyMeFileProcessorService, times(1))
                    .processEfficiencyMeFile("efficiency_me_test", horizon, STUDY_ID);
        }
    }

    @Test
    void uploadConstraintMeTrajectory_whenServiceThrowsBusinessException_returns400WithErrorDetails() throws Exception {
        when(constraintMeFileProcessorService.processConstraintMeFile("constraint_me_test", HORIZON, STUDY_ID))
                .thenThrow(BusinessException.builder()
                        .message("Areas {0} from CONSTRAINT_ME trajectory is (are) not present in AREAS_ME trajectory")
                        .errorMessageArguments(List.of("area_9"))
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());

        executePathSecurityLambda();

        mockMvc.perform(post("/v1/trajectory/constraint-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "constraint_me_test")
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("BUSINESS"))
                .andExpect(jsonPath("$.errorMessageArguments[0]").value("area_9"));
    }

    @Test
    void uploadEfficiencyMeTrajectory_whenServiceThrowsBusinessException_returns400WithErrorDetails() throws Exception {
        when(efficiencyMeFileProcessorService.processEfficiencyMeFile("efficiency_me_test", HORIZON, STUDY_ID))
                .thenThrow(BusinessException.builder()
                        .message("Missing horizon 2021 in EFFICIENCY_ME trajectory efficiency_me_test")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());

        executePathSecurityLambda();

        mockMvc.perform(post("/v1/trajectory/efficiency-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "efficiency_me_test")
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("BUSINESS"));
    }

    @Test
    void uploadEfficiencyMeTrajectory_whenPathSecurityRejects_returns400AndDoesNotCallService() throws Exception {
        doThrow(BusinessException.builder()
                .message("Invalid trajectory path")
                .httpStatus(HttpStatus.BAD_REQUEST)
                .build())
                .when(pathSecurityUtil).resolveSafePath(ArgumentMatchers.<Function<AntaresDataManagerProperties, Path>>any(), any());

        mockMvc.perform(post("/v1/trajectory/efficiency-me")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("trajectoryToUse", "efficiency_me_test")
                        .param("horizon", HORIZON)
                        .param("studyId", STUDY_ID.toString()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(efficiencyMeFileProcessorService);
    }

    // ==================== Helper ====================

    private void executePathSecurityLambda() {
        doAnswer(invocation -> {
            Function<AntaresDataManagerProperties, Path> lambda = invocation.getArgument(0);

            var mock = new AntaresDataManagerProperties();
            mock.nasDirectory = "/fake/nas";
            mock.trajectoryFilePath = "/fake/path";

            return lambda.apply(mock);
        }).when(pathSecurityUtil).resolveSafePath(ArgumentMatchers.<Function<AntaresDataManagerProperties, Path>>any(), any());
    }
}
