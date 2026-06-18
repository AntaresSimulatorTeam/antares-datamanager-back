package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.nuclear.NuclearFileProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NuclearControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    NuclearFileProcessorService nuclearFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    // ========== Success Cases ==========

    @Test
    void uploadNuclearModulationTrajectory_mustReturnCreated() throws Exception {

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("trajectory_test")
                .type("NUCLEAR_FR_MODULATION")
                .version(1)
                .horizon("2025-2026")
                .area("FR")
                .createdBy("testUser")
                .hasTimeSeries(true)
                .build();

        when(nuclearFileProcessorService.processNuclearModulationFile(
                anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(trajectory);

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trajectoryName").value("trajectory_test"))
                .andExpect(jsonPath("$.type").value("NUCLEAR_FR_MODULATION"))
                .andExpect(jsonPath("$.area").value("FR"));
    }

    @Test
    void uploadNuclearModulationTrajectory_withDifferentHorizons_mustReturnCreated() throws Exception {
        String[] horizons = {"2020-2021", "2025-2026", "2030-2031", "2035-2036"};

        for (String testHorizon : horizons) {
            TrajectoryEntity trajectory = TrajectoryEntity.builder()
                    .id(1)
                    .fileName("trajectory_test")
                    .type("NUCLEAR_FR_MODULATION")
                    .version(1)
                    .horizon(testHorizon)
                    .area("FR")
                    .createdBy("testUser")
                    .hasTimeSeries(true)
                    .build();

            when(nuclearFileProcessorService.processNuclearModulationFile(
                    anyString(), eq(testHorizon), anyInt(), anyString()))
                    .thenReturn(trajectory);

            this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                    .param("trajectoryToUse", "trajectory_test")
                    .param("horizon", testHorizon)
                    .param("studyId", "1")
                    .param("area", "FR"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.trajectoryName").value("trajectory_test"));
        }
    }

    @Test
    void uploadNuclearModulationTrajectory_withDifferentAreas_mustReturnCreated() throws Exception {
        String[] areas = {"FR", "DE", "IT", "ES", "BE"};

        for (String area : areas) {
            TrajectoryEntity trajectory = TrajectoryEntity.builder()
                    .id(1)
                    .fileName("trajectory_test")
                    .type("NUCLEAR_FR_MODULATION")
                    .version(1)
                    .horizon("2025-2026")
                    .area(area)
                    .createdBy("testUser")
                    .hasTimeSeries(true)
                    .build();

            when(nuclearFileProcessorService.processNuclearModulationFile(
                    anyString(), anyString(), anyInt(), eq(area)))
                    .thenReturn(trajectory);

            this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                    .param("trajectoryToUse", "trajectory_test")
                    .param("horizon", "2025-2026")
                    .param("studyId", "1")
                    .param("area", area))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.area").value(area));
        }
    }

    @Test
    void uploadNuclearModulationTrajectory_withValidTrajectoryNames_mustReturnCreated() throws Exception {
        String[] validNames = {"test_trajectory", "trajectory-test", "TRAJECTORY_TEST", "test123"};

        for (String name : validNames) {
            TrajectoryEntity trajectory = TrajectoryEntity.builder()
                    .id(1)
                    .fileName(name)
                    .type("NUCLEAR_FR_MODULATION")
                    .version(1)
                    .horizon("2025-2026")
                    .area("FR")
                    .createdBy("testUser")
                    .hasTimeSeries(true)
                    .build();

            when(nuclearFileProcessorService.processNuclearModulationFile(
                    eq(name), anyString(), anyInt(), anyString()))
                    .thenReturn(trajectory);

            this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                    .param("trajectoryToUse", name)
                    .param("horizon", "2025-2026")
                    .param("studyId", "1")
                    .param("area", "FR"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.trajectoryName").value(name));
        }
    }

    // ========== Validation Failure Cases - Horizon ==========

    @Test
    void uploadNuclearModulationTrajectory_withInvalidHorizonFormat_mustReturnBadRequest() throws Exception {

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "invalid-format")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNuclearModulationTrajectory_withMissingHorizon_mustReturnBadRequest() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNuclearModulationTrajectory_withOnlyYearHorizon_mustReturnBadRequest() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNuclearModulationTrajectory_withNonNumericHorizon_mustReturnBadRequest() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "yyyy-yyyy")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    // ========== Validation Failure Cases - Trajectory Name ==========

    @Test
    void uploadNuclearModulationTrajectory_withInvalidTrajectoryName_mustReturnBadRequest() throws Exception {

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "tra@jectory_test_with_invalid_chars")
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNuclearModulationTrajectory_withSpecialCharactersInName_mustReturnBadRequest() throws Exception {
        String[] invalidNames = {"test#trajectory", "traj ectory", "traj*ory", "traj!ory"};

        for (String name : invalidNames) {
            this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                    .param("trajectoryToUse", name)
                    .param("horizon", "2025-2026")
                    .param("studyId", "1")
                    .param("area", "FR"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void uploadNuclearModulationTrajectory_withTooLongTrajectoryName_mustReturnBadRequest() throws Exception {

        String longName = "a".repeat(41);

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", longName)
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNuclearModulationTrajectory_withExactlyMaxLengthTrajectoryName_mustReturnCreated() throws Exception {
        String maxName = "a".repeat(40);

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName(maxName)
                .type("NUCLEAR_FR_MODULATION")
                .version(1)
                .horizon("2025-2026")
                .area("FR")
                .createdBy("testUser")
                .hasTimeSeries(true)
                .build();

        when(nuclearFileProcessorService.processNuclearModulationFile(
                eq(maxName), anyString(), anyInt(), anyString()))
                .thenReturn(trajectory);

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", maxName)
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadNuclearModulationTrajectory_withMissingTrajectoryName_mustReturnBadRequest() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    // ========== Validation Failure Cases - Area ==========

    @Test
    void uploadNuclearModulationTrajectory_withInvalidAreaName_mustReturnBadRequest() throws Exception {

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", "FR@invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNuclearModulationTrajectory_withSpecialCharactersInArea_mustReturnBadRequest() throws Exception {
        String[] invalidAreas = {"F R", "FR#", "FR!", "FR$"};

        for (String area : invalidAreas) {
            this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                    .param("trajectoryToUse", "trajectory_test")
                    .param("horizon", "2025-2026")
                    .param("studyId", "1")
                    .param("area", area))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void uploadNuclearModulationTrajectory_withTooLongArea_mustReturnBadRequest() throws Exception {
        String longArea = "a".repeat(41);

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", longArea))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNuclearModulationTrajectory_withExactlyMaxLengthArea_mustReturnCreated() throws Exception {
        String maxArea = "a".repeat(40);

        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("trajectory_test")
                .type("NUCLEAR_FR_MODULATION")
                .version(1)
                .horizon("2025-2026")
                .area(maxArea)
                .createdBy("testUser")
                .hasTimeSeries(true)
                .build();

        when(nuclearFileProcessorService.processNuclearModulationFile(
                anyString(), anyString(), anyInt(), eq(maxArea)))
                .thenReturn(trajectory);

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", maxArea))
                .andExpect(status().isCreated());
    }

    @Test
    void uploadNuclearModulationTrajectory_withMissingArea_mustReturnBadRequest() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    // ========== Validation Failure Cases - Study ID ==========

    @Test
    void uploadNuclearModulationTrajectory_withMissingStudyId_mustReturnBadRequest() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNuclearModulationTrajectory_withNegativeStudyId_mustReturnCreated() throws Exception {
        // StudyId validation is done at service level, controller accepts negative values
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("trajectory_test")
                .type("NUCLEAR_FR_MODULATION")
                .version(1)
                .horizon("2025-2026")
                .area("FR")
                .createdBy("testUser")
                .hasTimeSeries(true)
                .build();

        when(nuclearFileProcessorService.processNuclearModulationFile(
                anyString(), anyString(), eq(-1), anyString()))
                .thenReturn(trajectory);

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("studyId", "-1")
                .param("area", "FR"))
                .andExpect(status().isCreated());
    }

    // ========== Service-level error handling ==========

    @Test
    void uploadNuclearModulationTrajectory_whenServiceThrowsBusinessException_mustReturnErrorResponse() throws Exception {
        when(nuclearFileProcessorService.processNuclearModulationFile(
                anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(BusinessException.builder()
                        .message("Nuclear modulation trajectory folder not found: {0}")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadNuclearModulationTrajectory_whenServiceThrowsConflictException_mustReturn409() throws Exception {
        when(nuclearFileProcessorService.processNuclearModulationFile(
                anyString(), anyString(), anyInt(), anyString()))
                .thenThrow(BusinessException.builder()
                        .message("Nuclear modulation trajectory {0} with the same checksum already exists")
                        .httpStatus(HttpStatus.CONFLICT)
                        .build());

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isConflict());
    }

    // ========== Response structure tests ==========

    @Test
    void uploadNuclearModulationTrajectory_responseContainsAllExpectedFields() throws Exception {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(123)
                .fileName("trajectory_test")
                .type("NUCLEAR_FR_MODULATION")
                .version(2)
                .horizon("2025-2026")
                .area("FR")
                .createdBy("testUser")
                .hasTimeSeries(true)
                .build();

        when(nuclearFileProcessorService.processNuclearModulationFile(
                anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(trajectory);

        this.mockMvc.perform(post("/v1/trajectory/nuclear-modulation")
                .param("trajectoryToUse", "trajectory_test")
                .param("horizon", "2025-2026")
                .param("studyId", "1")
                .param("area", "FR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trajectoryName").value("trajectory_test"))
                .andExpect(jsonPath("$.type").value("NUCLEAR_FR_MODULATION"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.area").value("FR"))
                .andExpect(jsonPath("$.hasTimeSeries").value(true));
    }
}
