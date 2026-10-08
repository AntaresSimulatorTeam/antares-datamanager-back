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

    // Tests for processConstraintMeTrajectory

}
