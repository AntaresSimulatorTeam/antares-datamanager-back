package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThermalFileProcessorServiceImplTest {
    private static final String THERMAL_CAPACITY_FILE_NAME = "thermal_BE_PEMMDB23_26avril";
    private static final String THERMAL_CAPACITY_PATH = "src/test/resources/thermal_capacity/" + THERMAL_CAPACITY_FILE_NAME + ".xlsx";
    private static final String THERMAL_PARAMETERS_FILE_NAME = "common_param_BP23_A_ref";
    private static final String THERMAL_PARAMETERS_PATH = "src/test/resources/thermal_parameters/" + THERMAL_PARAMETERS_FILE_NAME + ".xlsx";
    private static final String THERMAL_COSTS_FILE_NAME = "costs_BP23_A_ref";
    private static final String THERMAL_COSTS_PATH = "src/test/resources/thermal_cost/" + THERMAL_COSTS_FILE_NAME + ".xlsx";

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private ThermalCostTypeRepository thermalCostTypeRepository;

    @InjectMocks
    private ThermalFileProcessorServiceImpl thermalFileProcessorService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processThermalCapacityFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn(THERMAL_CAPACITY_PATH);
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(path.getFileName().toString()).thenReturn(THERMAL_CAPACITY_FILE_NAME + ".xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        var horizon = "2023-2024";
        thermalFileProcessorService.processThermalFile(path, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_CAPACITY);

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processThermalCapacityFile_whenTrajectoryDoesNotExist() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn(THERMAL_CAPACITY_PATH);
        when(path.getFileName().toString()).thenReturn(THERMAL_CAPACITY_FILE_NAME + ".xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());
        var horizon = "2023-2024";
        thermalFileProcessorService.processThermalFile(path, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_PARAMETER);

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processThermalParameterFile() throws IOException {
        // Given

        Path path = Path.of(THERMAL_PARAMETERS_PATH);

        String horizon = "2025";

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .fileName(THERMAL_PARAMETERS_FILE_NAME)
                .type(TrajectoryType.THERMAL_PARAMETER.name())
                .version(1)
                .horizon(horizon)
                .build();


        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(THERMAL_PARAMETERS_FILE_NAME + ".xlsx"))
                .thenReturn(Optional.of(expectedTrajectory));
        when(trajectoryRepository.save(any())).thenReturn(expectedTrajectory);

        // When
        thermalFileProcessorService.processThermalFile(path, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_PARAMETER);

        // Then
        verify(trajectoryRepository, times(1))
                .findFirstByFileNameOrderByVersionDesc(THERMAL_PARAMETERS_FILE_NAME);

        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));

    }

    @Test
    void processThermalCostFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        // Given
        Path path = mock(Path.class);
        when(path.getFileName().toString()).thenReturn(THERMAL_COSTS_FILE_NAME + ".xlsx");
        when(path.toString()).thenReturn(THERMAL_COSTS_PATH);
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        String horizon = "2025";

        // When
        thermalFileProcessorService.processThermalFile(path, horizon, thermalFileProcessorService::buildThermalCosts, TrajectoryType.THERMAL_COST);

        // Then
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void processThermalCostFile_whenTrajectoryDoesNotExist() throws IOException {
        // Given
        Path path = mock(Path.class);
        when(path.getFileName().toString()).thenReturn(THERMAL_COSTS_FILE_NAME + ".xlsx");
        when(path.toString()).thenReturn(THERMAL_COSTS_PATH);
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());
        String horizon = "2025";

        // When
        thermalFileProcessorService.processThermalFile(path, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_PARAMETER);

        // Then
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void buildThermalParameters() throws IOException {
        // Given
        Path path = Path.of(THERMAL_PARAMETERS_PATH);

        // When
        List<ThermalParameterEntity> thermalParameters = thermalFileProcessorService.buildThermalParameters(path);

        // Then
        assertEquals(47, thermalParameters.size());
    }

    @Test
    void saveThermalCapacitiesTrajectory() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        List<ThermalClusterCapacityEntity> thermalClusterCapacities = List.of(new ThermalClusterCapacityEntity());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);

        // When
        TrajectoryEntity result = thermalFileProcessorService.saveThermalTrajectory(trajectory, thermalClusterCapacities, TrajectoryType.THERMAL_CAPACITY);

        // Then
        assertEquals(TrajectoryType.THERMAL_CAPACITY.name(), result.getType());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void saveThermalParametersTrajectory() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        List<ThermalParameterEntity> thermalParameterEntities = List.of(new ThermalParameterEntity());

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);

        // When
        TrajectoryEntity result = thermalFileProcessorService.saveThermalTrajectory(trajectory, thermalParameterEntities, TrajectoryType.THERMAL_PARAMETER);

        // Then
        assertEquals(TrajectoryType.THERMAL_PARAMETER.name(), result.getType());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }

    @Test
    void saveThermalCostTrajectory() {
        // Given
        TrajectoryEntity trajectory = new TrajectoryEntity();
        List<ThermalCostEntity> thermalCostEntities = List.of(new ThermalCostEntity(10.0, 2036.0, new ThermalCostTypeEntity()));

        when(trajectoryRepository.save(any(TrajectoryEntity.class))).thenReturn(trajectory);

        // When
        TrajectoryEntity result = thermalFileProcessorService.saveThermalTrajectory(trajectory, thermalCostEntities, TrajectoryType.THERMAL_COST);

        // Then
        assertEquals(TrajectoryType.THERMAL_COST.name(), result.getType());
        verify(trajectoryRepository).save(any(TrajectoryEntity.class));
    }
}
