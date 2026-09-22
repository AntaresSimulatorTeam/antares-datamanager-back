package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.hydro.HydroParametersMeFileProcessorService;
import com.rte_france.antares.datamanager_back.util.PathSecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("MultiEnergyController - uploadHydroParametersMeTrajectory Tests")
class MultiEnergyControllerHydroParametersMeTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    private HydroParametersMeFileProcessorService hydroParametersMeFileProcessorService;

    @MockBean
    private PathSecurityUtil pathSecurityUtil;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // ==================== Success Path Tests ====================

    @Test
    @DisplayName("Should successfully upload HYDRO_PARAMETERS_ME trajectory")
    void testUploadHydroParametersMeTrajectorySuccess() throws Exception {
        TrajectoryEntity trajectoryEntity = TrajectoryEntity.builder()
                .id(1)
                .fileName("test_trajectory")
                .horizon("2024-2025")
                .type("HYDRO_PARAMETERS_ME")
                .version(1)
                .checksum("abc123")
                .createdBy("USER123")
                .build();

        when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory("test_trajectory", "2024-2025", 1))
                .thenReturn(trajectoryEntity);
        when(pathSecurityUtil.resolveSafePath(any(java.util.function.Function.class))).thenReturn(Path.of("/test"));

        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2024-2025")
                        .param("studyId", "1"))
                .andExpect(status().isCreated());
    }

    // ==================== Parameter Validation Tests ====================

    @Test
    @DisplayName("Should return 400 when trajectoryToUse parameter is missing")
    void testMissingTrajectoryParam() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("horizon", "2024-2025")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when horizon parameter is missing")
    void testMissingHorizonParam() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when studyId parameter is missing")
    void testMissingStudyIdParam() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2024-2025"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when all parameters are missing")
    void testAllParamsMissing() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me"))
                .andExpect(status().isBadRequest());
    }

    // ==================== Horizon Format Validation Tests ====================

    @Test
    @DisplayName("Should return 400 for invalid horizon format - single year")
    void testInvalidHorizonFormat_SingleYear() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2024")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for invalid horizon format - no delimiter")
    void testInvalidHorizonFormat_NoDelimiter() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "20242025")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for invalid horizon format - wrong delimiter")
    void testInvalidHorizonFormat_WrongDelimiter() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2024/2025")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for invalid horizon format - non-numeric values")
    void testInvalidHorizonFormat_NonNumeric() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "abcd-efgh")
                        .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should accept valid horizon format YYYY-YYYY")
    void testValidHorizonFormat() throws Exception {
        TrajectoryEntity trajectoryEntity = TrajectoryEntity.builder()
                .id(1)
                .fileName("test_trajectory")
                .horizon("2024-2025")
                .type("HYDRO_PARAMETERS_ME")
                .version(1)
                .build();

        when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory("test_trajectory", "2024-2025", 1))
                .thenReturn(trajectoryEntity);
        when(pathSecurityUtil.resolveSafePath(any(java.util.function.Function.class))).thenReturn(Path.of("/test"));

        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2024-2025")
                        .param("studyId", "1"))
                .andExpect(status().isCreated());
    }

    // ==================== StudyId Validation Tests ====================

    @Test
    @DisplayName("Should return 400 when studyId is not an integer")
    void testStudyIdNotInteger() throws Exception {
        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2024-2025")
                        .param("studyId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should accept valid integer studyId")
    void testValidStudyId() throws Exception {
        TrajectoryEntity trajectoryEntity = TrajectoryEntity.builder()
                .id(1)
                .fileName("test_trajectory")
                .horizon("2024-2025")
                .type("HYDRO_PARAMETERS_ME")
                .version(1)
                .build();

        when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory("test_trajectory", "2024-2025", 999))
                .thenReturn(trajectoryEntity);
        when(pathSecurityUtil.resolveSafePath(any(java.util.function.Function.class))).thenReturn(Path.of("/test"));

        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2024-2025")
                        .param("studyId", "999"))
                .andExpect(status().isCreated());
    }

    // ==================== HTTP Method Tests ====================

    @Test
    @DisplayName("Only POST method is accepted for hydro-parameters-me endpoint")
    void testOnlyPostMethodAccepted() throws Exception {
        mockMvc.perform(get("/v1/trajectory/hydro-parameters-me"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ==================== Multiple Horizons Tests ====================

    @Test
    @DisplayName("Should handle different horizons for same trajectory")
    void testMultipleHorizonsForSameTrajectory() throws Exception {
        TrajectoryEntity trajectory2024 = TrajectoryEntity.builder()
                .id(1)
                .fileName("test_trajectory")
                .horizon("2024-2025")
                .type("HYDRO_PARAMETERS_ME")
                .version(1)
                .build();

        TrajectoryEntity trajectory2025 = TrajectoryEntity.builder()
                .id(2)
                .fileName("test_trajectory")
                .horizon("2025-2026")
                .type("HYDRO_PARAMETERS_ME")
                .version(1)
                .build();

        when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory("test_trajectory", "2024-2025", 1))
                .thenReturn(trajectory2024);
        when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory("test_trajectory", "2025-2026", 1))
                .thenReturn(trajectory2025);
        when(pathSecurityUtil.resolveSafePath(any(java.util.function.Function.class))).thenReturn(Path.of("/test"));

        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2024-2025")
                        .param("studyId", "1"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2025-2026")
                        .param("studyId", "1"))
                .andExpect(status().isCreated());
    }

    // ==================== Edge Case Tests ====================

    @Test
    @DisplayName("Should handle very large studyId")
    void testLargeStudyId() throws Exception {
        TrajectoryEntity trajectoryEntity = TrajectoryEntity.builder()
                .id(1)
                .fileName("test_trajectory")
                .horizon("2024-2025")
                .type("HYDRO_PARAMETERS_ME")
                .version(1)
                .build();

        when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory("test_trajectory", "2024-2025", 2147483647))
                .thenReturn(trajectoryEntity);
        when(pathSecurityUtil.resolveSafePath(any(java.util.function.Function.class))).thenReturn(Path.of("/test"));

        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2024-2025")
                        .param("studyId", "2147483647"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Should handle future horizons")
    void testFutureHorizon() throws Exception {
        TrajectoryEntity trajectoryEntity = TrajectoryEntity.builder()
                .id(1)
                .fileName("test_trajectory")
                .horizon("2050-2051")
                .type("HYDRO_PARAMETERS_ME")
                .version(1)
                .build();

        when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory("test_trajectory", "2050-2051", 1))
                .thenReturn(trajectoryEntity);
        when(pathSecurityUtil.resolveSafePath(any(java.util.function.Function.class))).thenReturn(Path.of("/test"));

        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2050-2051")
                        .param("studyId", "1"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Should handle past horizons")
    void testPastHorizon() throws Exception {
        TrajectoryEntity trajectoryEntity = TrajectoryEntity.builder()
                .id(1)
                .fileName("test_trajectory")
                .horizon("2020-2021")
                .type("HYDRO_PARAMETERS_ME")
                .version(1)
                .build();

        when(hydroParametersMeFileProcessorService.processHydroParametersMeDirectory("test_trajectory", "2020-2021", 1))
                .thenReturn(trajectoryEntity);
        when(pathSecurityUtil.resolveSafePath(any(java.util.function.Function.class))).thenReturn(Path.of("/test"));

        mockMvc.perform(post("/v1/trajectory/hydro-parameters-me")
                        .param("trajectoryToUse", "test_trajectory")
                        .param("horizon", "2020-2021")
                        .param("studyId", "1"))
                .andExpect(status().isCreated());
    }
}
