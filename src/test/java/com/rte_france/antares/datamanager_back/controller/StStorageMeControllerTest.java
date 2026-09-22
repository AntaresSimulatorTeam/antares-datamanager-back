package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.service.sts.StStorageMeFileProcessorService;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StStorageMeControllerTest {

    @Autowired
    protected WebApplicationContext wac;

    protected MockMvc mockMvc;

    @MockBean
    private StStorageMeFileProcessorService stStorageMeFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void postValidRequest_should_returnCreated_and_callService() throws Exception {
        // Given
        String trajectoryToUse = "me_test.xlsx";
        String horizon = "2025-2026";
        String studyId = "1";

        TrajectoryEntity fakeEntity = Mockito.mock(TrajectoryEntity.class);
        when(stStorageMeFileProcessorService.processStStorageMeFile(any(String.class), any(String.class), anyInt()))
                .thenReturn(fakeEntity);

        // When / Then
        mockMvc.perform(post("/v1/trajectory/st-storage-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", studyId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        verify(stStorageMeFileProcessorService, times(1))
                .processStStorageMeFile(any(String.class), any(String.class), anyInt());
    }

    @Test
    void postWithTooLongTrajectoryName_should_returnBadRequest() throws Exception {
        // trajectoryToUse > 40 chars (excluding .xlsx)
        String trajectoryToUse = "me_" + "a".repeat(38) + ".xlsx";
        String horizon = "2025-2026";

        mockMvc.perform(post("/v1/trajectory/st-storage-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // service should not be called
        verify(stStorageMeFileProcessorService, times(0)).processStStorageMeFile(any(String.class), any(String.class), anyInt());
    }

    @Test
    void postWithInvalidHorizon_should_returnBadRequest() throws Exception {
        String trajectoryToUse = "me_test.xlsx";
        String invalidHorizon = "2025-invalid";

        mockMvc.perform(post("/v1/trajectory/st-storage-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", invalidHorizon)
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // service should not be called
        verify(stStorageMeFileProcessorService, times(0)).processStStorageMeFile(any(String.class), any(String.class), anyInt());
    }

    @Test
    void postWithMissingTrajectoryToUse_should_returnBadRequest() throws Exception {
        String horizon = "2025-2026";

        mockMvc.perform(post("/v1/trajectory/st-storage-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("horizon", horizon)
                        .param("studyId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // service should not be called
        verify(stStorageMeFileProcessorService, times(0)).processStStorageMeFile(any(String.class), any(String.class), anyInt());
    }

    @Test
    void postWithInvalidStudyId_should_returnBadRequest() throws Exception {
        String trajectoryToUse = "me_test.xlsx";
        String horizon = "2025-2026";

        mockMvc.perform(post("/v1/trajectory/st-storage-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("trajectoryToUse", trajectoryToUse)
                        .param("horizon", horizon)
                        .param("studyId", "invalid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // service should not be called
        verify(stStorageMeFileProcessorService, times(0)).processStStorageMeFile(any(String.class), any(String.class), anyInt());
    }
}
