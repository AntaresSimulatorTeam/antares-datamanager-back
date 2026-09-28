package com.rte_france.antares.datamanager_back.controller;

import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.hydro.HydroMeFileProcessorService;
import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("MultiEnergyController - HYDRO_WATER_VALUES_ME Tests")
class MultiEnergyControllerHydroWaterValuesMeTest {

    private static final String TRAJECTORY_NAME = "water_values_trajectory";
    private static final String HORIZON = "2029-2030";
    private static final Integer STUDY_ID = 1;

    @Autowired
    protected WebApplicationContext wac;

    private MockMvc mockMvc;

    @MockBean
    private HydroMeFileProcessorService hydroMeFileProcessorService;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    @DisplayName("Should upload HYDRO_WATER_VALUES_ME trajectory and return 201")
    void uploadHydroWaterValuesMeTrajectory_returns201_andCallsService() throws Exception {
        // Arrange
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(1);
        entity.setFileName(TRAJECTORY_NAME);
        entity.setType(TrajectoryType.HYDRO_WATER_VALUES_ME.name());
        entity.setHorizon(HORIZON);
        entity.setVersion(1);
        entity.setChecksum("test_checksum");
        entity.setCreatedBy("TEST_USER");

        when(hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                eq(TRAJECTORY_NAME), eq(HORIZON), eq(STUDY_ID)))
                .thenReturn(entity);

        // Act & Assert
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", TRAJECTORY_NAME)
                .param("horizon", HORIZON)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value(TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .andExpect(jsonPath("$.horizon").value(HORIZON));

        verify(hydroMeFileProcessorService, times(1))
                .processHydroWaterValuesMeDirectory(TRAJECTORY_NAME, HORIZON, STUDY_ID);
    }

    @Test
    @DisplayName("Should return 400 when trajectoryToUse exceeds 40 characters")
    void uploadHydroWaterValuesMeTrajectory_whenTrajectoryNameTooLong_returns400() throws Exception {
        // Arrange
        String longName = "this_is_a_very_long_trajectory_name_exceeding_forty_chars";

        // Act & Assert
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", longName)
                .param("horizon", HORIZON)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isBadRequest());

        verify(hydroMeFileProcessorService, never())
                .processHydroWaterValuesMeDirectory(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 when horizon format is invalid")
    void uploadHydroWaterValuesMeTrajectory_whenHorizonFormatInvalid_returns400() throws Exception {
        // Arrange
        String invalidHorizon = "2029/2030"; // Invalid format

        // Act & Assert
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", TRAJECTORY_NAME)
                .param("horizon", invalidHorizon)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isBadRequest());

        verify(hydroMeFileProcessorService, never())
                .processHydroWaterValuesMeDirectory(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 when studyId is missing")
    void uploadHydroWaterValuesMeTrajectory_whenStudyIdMissing_returns400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", TRAJECTORY_NAME)
                .param("horizon", HORIZON))
                .andExpect(status().isBadRequest());

        verify(hydroMeFileProcessorService, never())
                .processHydroWaterValuesMeDirectory(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 when trajectoryToUse is missing")
    void uploadHydroWaterValuesMeTrajectory_whenTrajectoryNameMissing_returns400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("horizon", HORIZON)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isBadRequest());

        verify(hydroMeFileProcessorService, never())
                .processHydroWaterValuesMeDirectory(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 when horizon is missing")
    void uploadHydroWaterValuesMeTrajectory_whenHorizonMissing_returns400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", TRAJECTORY_NAME)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isBadRequest());

        verify(hydroMeFileProcessorService, never())
                .processHydroWaterValuesMeDirectory(any(), any(), any());
    }

    @Test
    @DisplayName("Should return 400 when trajectory with different content exists but version already incremented")
    void uploadHydroWaterValuesMeTrajectory_whenMultipleVersionsExist_returns201() throws Exception {
        // Arrange
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(2);
        entity.setFileName(TRAJECTORY_NAME);
        entity.setType(TrajectoryType.HYDRO_WATER_VALUES_ME.name());
        entity.setHorizon(HORIZON);
        entity.setVersion(2);

        when(hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                eq(TRAJECTORY_NAME), eq(HORIZON), eq(STUDY_ID)))
                .thenReturn(entity);

        // Act & Assert
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", TRAJECTORY_NAME)
                .param("horizon", HORIZON)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));

        verify(hydroMeFileProcessorService, times(1))
                .processHydroWaterValuesMeDirectory(TRAJECTORY_NAME, HORIZON, STUDY_ID);
    }

    @Test
    @DisplayName("Should return correct response with all trajectory details")
    void uploadHydroWaterValuesMeTrajectory_returns201_withCompleteDetails() throws Exception {
        // Arrange
        TrajectoryEntity entity = TrajectoryEntity.builder()
                .id(42)
                .fileName(TRAJECTORY_NAME)
                .type(TrajectoryType.HYDRO_WATER_VALUES_ME.name())
                .horizon(HORIZON)
                .version(1)
                .checksum("abc123def456")
                .createdBy("USER123")
                .fileSize(1024L)
                .build();

        when(hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                eq(TRAJECTORY_NAME), eq(HORIZON), eq(STUDY_ID)))
                .thenReturn(entity);

        // Act & Assert
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", TRAJECTORY_NAME)
                .param("horizon", HORIZON)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.type").value(TrajectoryType.HYDRO_WATER_VALUES_ME.name()))
                .andExpect(jsonPath("$.horizon").value(HORIZON))
                .andExpect(jsonPath("$.version").value(1));

        verify(hydroMeFileProcessorService, times(1))
                .processHydroWaterValuesMeDirectory(TRAJECTORY_NAME, HORIZON, STUDY_ID);
    }

    @Test
    @DisplayName("Should accept valid trajectory names with underscores and hyphens")
    void uploadHydroWaterValuesMeTrajectory_withValidNameFormats_returns201() throws Exception {
        // Arrange
        String trajectoryName = "water-values_v1";

        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(1);
        entity.setFileName(trajectoryName);
        entity.setType(TrajectoryType.HYDRO_WATER_VALUES_ME.name());
        entity.setHorizon(HORIZON);
        entity.setVersion(1);

        when(hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                eq(trajectoryName), eq(HORIZON), eq(STUDY_ID)))
                .thenReturn(entity);

        // Act & Assert
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", trajectoryName)
                .param("horizon", HORIZON)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isCreated());

        verify(hydroMeFileProcessorService, times(1))
                .processHydroWaterValuesMeDirectory(trajectoryName, HORIZON, STUDY_ID);
    }

    @Test
    @DisplayName("Should accept different horizon formats (valid yyyy-yyyy)")
    void uploadHydroWaterValuesMeTrajectory_withDifferentValidHorizons_returns201() throws Exception {
        // Arrange
        String horizon1 = "2020-2021";
        String horizon2 = "2099-2100";

        TrajectoryEntity entity1 = new TrajectoryEntity();
        entity1.setId(1);
        entity1.setFileName(TRAJECTORY_NAME);
        entity1.setType(TrajectoryType.HYDRO_WATER_VALUES_ME.name());
        entity1.setHorizon(horizon1);
        entity1.setVersion(1);

        when(hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                eq(TRAJECTORY_NAME), eq(horizon1), eq(STUDY_ID)))
                .thenReturn(entity1);

        // Act & Assert - horizon1
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", TRAJECTORY_NAME)
                .param("horizon", horizon1)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isCreated());

        // Arrange - horizon2
        entity1.setHorizon(horizon2);
        when(hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                eq(TRAJECTORY_NAME), eq(horizon2), eq(STUDY_ID)))
                .thenReturn(entity1);

        // Act & Assert - horizon2
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", TRAJECTORY_NAME)
                .param("horizon", horizon2)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Should call service with correct parameters")
    void uploadHydroWaterValuesMeTrajectory_callsServiceWithCorrectParameters() throws Exception {
        // Arrange
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(1);
        entity.setFileName(TRAJECTORY_NAME);
        entity.setType(TrajectoryType.HYDRO_WATER_VALUES_ME.name());
        entity.setHorizon(HORIZON);
        entity.setVersion(1);

        when(hydroMeFileProcessorService.processHydroWaterValuesMeDirectory(
                any(), any(), any()))
                .thenReturn(entity);

        // Act
        mockMvc.perform(post("/v1/trajectory/hydro-water-values-me")
                .contentType(MediaType.APPLICATION_JSON)
                .param("trajectoryToUse", TRAJECTORY_NAME)
                .param("horizon", HORIZON)
                .param("studyId", String.valueOf(STUDY_ID)))
                .andExpect(status().isCreated());

        // Assert - Verify the service was called with exact parameters
        verify(hydroMeFileProcessorService, times(1))
                .processHydroWaterValuesMeDirectory(TRAJECTORY_NAME, HORIZON, STUDY_ID);
    }
}
