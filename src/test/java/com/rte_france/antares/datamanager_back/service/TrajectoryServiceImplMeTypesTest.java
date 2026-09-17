package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.common.impl.TrajectoryServiceImpl;
import com.rte_france.antares.datamanager_back.service.efficiency_me.EfficiencyMeFileProcessorService;
import com.rte_france.antares.datamanager_back.service.constraint_me.ConstraintMeFileProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrajectoryServiceImplMeTypesTest {

    @Mock
    EfficiencyMeFileProcessorService efficiencyMeFileProcessorService;

    @Mock
    ConstraintMeFileProcessorService constraintMeFileProcessorService;

    @InjectMocks
    TrajectoryServiceImpl trajectoryService;

    private TrajectoryEntity mockTrajectoryEntity;

    @BeforeEach
    void setUp() {
        mockTrajectoryEntity = TrajectoryEntity.builder()
                .id(1)
                .fileName("test_trajectory")
                .type(TrajectoryType.EFFICIENCY_ME.name())
                .version(1)
                .horizon("2023-2024")
                .build();
    }

    // Tests for processEfficiencyMeTrajectory
    @Test
    void processEfficiencyMeTrajectory_withValidParams_returnsTrajectory() throws IOException {
        // Given
        String trajectoryName = "efficiency_me_test";
        String horizon = "2023-2024";
        Integer studyId = 1;

        when(efficiencyMeFileProcessorService.processEfficiencyMeFile(trajectoryName, horizon, studyId))
                .thenReturn(mockTrajectoryEntity);

        // When
        TrajectoryEntity result = trajectoryService.processEfficiencyMeTrajectory(trajectoryName, horizon, studyId);

        // Then
        assertNotNull(result);
        assertEquals("test_trajectory", result.getFileName());
        assertEquals(TrajectoryType.EFFICIENCY_ME.name(), result.getType());
        verify(efficiencyMeFileProcessorService, times(1)).processEfficiencyMeFile(trajectoryName, horizon, studyId);
    }

    @Test
    void processEfficiencyMeTrajectory_whenServiceThrowsIOException_propagatesException() throws IOException {
        // Given
        String trajectoryName = "efficiency_me_test";
        String horizon = "2023-2024";
        Integer studyId = 1;

        when(efficiencyMeFileProcessorService.processEfficiencyMeFile(trajectoryName, horizon, studyId))
                .thenThrow(new IOException("File not found"));

        // When & Then
        assertThrows(IOException.class, () ->
                trajectoryService.processEfficiencyMeTrajectory(trajectoryName, horizon, studyId)
        );
    }

    // Tests for processConstraintMeTrajectory
    @Test
    void processConstraintMeTrajectory_withValidParams_returnsTrajectory() throws IOException {
        // Given
        String trajectoryName = "constraint_me_test";
        String horizon = "2023-2024";
        Integer studyId = 1;

        TrajectoryEntity constraintEntity = TrajectoryEntity.builder()
                .id(2)
                .fileName(trajectoryName)
                .type(TrajectoryType.CONSTRAINT_ME.name())
                .version(1)
                .horizon(horizon)
                .build();

        when(constraintMeFileProcessorService.processConstraintMeFile(trajectoryName, horizon, studyId))
                .thenReturn(constraintEntity);

        // When
        TrajectoryEntity result = trajectoryService.processConstraintMeTrajectory(trajectoryName, horizon, studyId);

        // Then
        assertNotNull(result);
        assertEquals(trajectoryName, result.getFileName());
        assertEquals(TrajectoryType.CONSTRAINT_ME.name(), result.getType());
        verify(constraintMeFileProcessorService, times(1)).processConstraintMeFile(trajectoryName, horizon, studyId);
    }

    @Test
    void processConstraintMeTrajectory_whenServiceThrowsIOException_propagatesException() throws IOException {
        // Given
        String trajectoryName = "constraint_me_test";
        String horizon = "2023-2024";
        Integer studyId = 1;

        when(constraintMeFileProcessorService.processConstraintMeFile(trajectoryName, horizon, studyId))
                .thenThrow(new IOException("File processing error"));

        // When & Then
        assertThrows(IOException.class, () ->
                trajectoryService.processConstraintMeTrajectory(trajectoryName, horizon, studyId)
        );
    }

}
