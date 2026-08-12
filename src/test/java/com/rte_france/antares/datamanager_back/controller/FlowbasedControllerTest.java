package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.exception.BusinessException;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.flowbased.FlowbasedFileProcessorService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlowbasedControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    FlowbasedFileProcessorService flowbasedFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .build();
    }

    @Test
    void uploadFlowbasedTrajectory_withValidParameters_shouldReturnCreated() throws Exception {
        TrajectoryEntity trajectory = TrajectoryEntity.builder()
                .id(1)
                .fileName("repo1###repo2")
                .type("FLOWBASED")
                .version(1)
                .horizon("2030-2031")
                .createdBy("testUser")
                .checksum("abc123def456")
                .build();

        when(flowbasedFileProcessorService.processFlowbasedFiles(
                any(), eq("repo1###repo2"), eq(1), eq("2030-2031")))
                .thenReturn(trajectory);

        this.mockMvc.perform(post("/v1/trajectory/flowbased")
                .param("trajectoryToUse", "repo1###repo2")
                .param("horizon", "2030-2031")
                .param("studyId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trajectoryName").value("repo1/repo2"))
                .andExpect(jsonPath("$.type").value("FLOWBASED"))
                .andExpect(jsonPath("$.horizon").value("2030-2031"));
    }

    @Test
    void uploadFlowbasedTrajectory_withDifferentHorizons_shouldReturnCreated() throws Exception {
        String[] horizons = {"2025-2026", "2030-2031", "2035-2036"};

        for (String testHorizon : horizons) {
            TrajectoryEntity trajectory = TrajectoryEntity.builder()
                    .id(1)
                    .fileName("repo1###repo2")
                    .type("FLOWBASED")
                    .version(1)
                    .horizon(testHorizon)
                    .createdBy("testUser")
                    .checksum("abc123")
                    .build();

            when(flowbasedFileProcessorService.processFlowbasedFiles(
                    any(), eq("repo1###repo2"), eq(1), eq(testHorizon)))
                    .thenReturn(trajectory);

            this.mockMvc.perform(post("/v1/trajectory/flowbased")
                    .param("trajectoryToUse", "repo1###repo2")
                    .param("horizon", testHorizon)
                    .param("studyId", "1"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.horizon").value(testHorizon));
        }
    }

    @Test
    void uploadFlowbasedTrajectory_withInvalidFormat_shouldReturnBadRequest() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/flowbased")
                .param("trajectoryToUse", "invalidFormat")
                .param("horizon", "2030-2031")
                .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFlowbasedTrajectory_withMissingHorizon_shouldReturnBadRequest() throws Exception {
        this.mockMvc.perform(post("/v1/trajectory/flowbased")
                .param("trajectoryToUse", "repo1_repo2")
                .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFlowbasedTrajectory_whenServiceThrowsBusinessException_shouldReturnBadRequest() throws Exception {
        when(flowbasedFileProcessorService.processFlowbasedFiles(
                any(), anyString(), anyInt(), anyString()))
                .thenThrow(BusinessException.builder()
                        .message("File already processed with same content")
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .build());

        this.mockMvc.perform(post("/v1/trajectory/flowbased")
                .param("trajectoryToUse", "repo1_repo2")
                .param("horizon", "2030-2031")
                .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFlowbasedTrajectory_withTrajectorynameExceedingMaxLength_shouldReturnBadRequest() throws Exception {
        String longName = "a".repeat(50) + "_" + "b".repeat(50);

        this.mockMvc.perform(post("/v1/trajectory/flowbased")
                .param("trajectoryToUse", longName)
                .param("horizon", "2030-2031")
                .param("studyId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFlowbasedTrajectory_withMultipleValidCalls_shouldReturnCreatedEachTime() throws Exception {
        TrajectoryEntity trajectory1 = TrajectoryEntity.builder()
                .id(1)
                .fileName("repo1###repo2")
                .type("FLOWBASED")
                .version(1)
                .horizon("2030-2031")
                .createdBy("user1")
                .checksum("checksum1")
                .build();

        TrajectoryEntity trajectory2 = TrajectoryEntity.builder()
                .id(2)
                .fileName("repo3###repo4")
                .type("FLOWBASED")
                .version(1)
                .horizon("2025-2026")
                .createdBy("user2")
                .checksum("checksum2")
                .build();

        when(flowbasedFileProcessorService.processFlowbasedFiles(
                any(), eq("repo1###repo2"), eq(1), eq("2030-2031")))
                .thenReturn(trajectory1);

        when(flowbasedFileProcessorService.processFlowbasedFiles(
                any(), eq("repo3###repo4"), eq(2), eq("2025-2026")))
                .thenReturn(trajectory2);

        this.mockMvc.perform(post("/v1/trajectory/flowbased")
                .param("trajectoryToUse", "repo1###repo2")
                .param("horizon", "2030-2031")
                .param("studyId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trajectoryName").value("repo1/repo2"));

        this.mockMvc.perform(post("/v1/trajectory/flowbased")
                .param("trajectoryToUse", "repo3###repo4")
                .param("horizon", "2025-2026")
                .param("studyId", "2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trajectoryName").value("repo3/repo4"));
    }
}
