package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.repository.*;
import com.rte_france.antares.datamanager_back.repository.model.ThermalModulationParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalClusterRefService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalEconomicCostAndRateService;
import com.rte_france.antares.datamanager_back.service.thermal.ThermalParamModulationService;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalControlsServiceImpl;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.thermal.impl.ThermalParamModulationServiceImpl;
import com.rte_france.antares.datamanager_back.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)

class ThermalParamModulationServiceImplTest {
    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ThermalParamModulationServiceImpl thermalParamModulationService;



    @Test
    void processThermalModulationParameterFile_shouldSaveNewTrajectoryWhenNoExistingTrajectory(@TempDir Path tempDir) throws IOException {
        String trajectoryToUse = "modulation_trajectory";
        String paramModulationDir = "thermal";


        Path trajectoryPath = tempDir.resolve(paramModulationDir).resolve(trajectoryToUse);
        Files.createDirectories(trajectoryPath);
        String horizon = "2025-2026";
        List<ThermalModulationParameterEntity> entities = List.of(new ThermalModulationParameterEntity());
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonAndTypeOrderByVersionDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(trajectoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TrajectoryEntity result = thermalParamModulationService.processThermalModulationParameterFile(trajectoryPath, horizon, entities, TrajectoryType.THERMAL_TECHNICAL_MODULATION_PARAMETER);

        assertNotNull(result);
        assertEquals("THERMAL_TECHNICAL_MODULATION_PARAMETER", result.getType());
        assertEquals(1, result.getVersion());
        verify(trajectoryRepository).save(any());
    }

}
