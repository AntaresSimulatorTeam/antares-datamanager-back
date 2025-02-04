package com.rte_france.antares.datamanager_back.service;



import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;

import com.rte_france.antares.datamanager_back.repository.model.*;
import com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;



import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThermalFileProcessorServiceImplTest {
    private static final String thermalCapacityFileName = "thermal_BE_PEMMDB23_26avril";
    private static final String thermalCapacityPath = "src/test/resources/thermal_capacity/" + thermalCapacityFileName + ".xlsx";
    private static final String thermalParametersFileName = "common_param_BP23_A_ref";
    private static final String thermalParametersPath = "src/test/resources/thermal_parameters/" + thermalParametersFileName + ".xlsx";
    private static final String thermalCostsFileName = "costs_BP23_A_ref";
    private static final String thermalCostsPath = "src/test/resources/thermal_cost/" + thermalCostsFileName + ".xlsx";

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
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn(thermalCapacityPath);
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(file.getName()).thenReturn(thermalCapacityFileName + ".xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        var horizon = "2023-2024";
        thermalFileProcessorService.processThermalFile(file, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_CAPACITY);

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processThermalCapacityFile_whenTrajectoryDoesNotExist() throws IOException {
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn(thermalCapacityPath);
        when(file.getName()).thenReturn(thermalCapacityFileName + ".xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());
        var horizon = "2023-2024";
        thermalFileProcessorService.processThermalFile(file, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_PARAMETER);

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processThermalParameterFile() throws IOException {
        // Given

        Path filePath = Paths.get(thermalParametersPath);
        File file = filePath.toFile();

        String horizon = "2025";

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .fileName(thermalParametersFileName)
                .type(TrajectoryType.THERMAL_PARAMETER.name())
                .version(1)
                .horizon(horizon)
                .build();


        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(thermalParametersFileName + ".xlsx"))
                .thenReturn(Optional.of(expectedTrajectory));
        when(trajectoryRepository.save(any())).thenReturn(expectedTrajectory);

        // When
        thermalFileProcessorService.processThermalFile(file, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_PARAMETER);

        // Then
        verify(trajectoryRepository, times(1))
                .findFirstByFileNameOrderByVersionDesc(thermalParametersFileName);

        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));

    }

    @Test
    void processThermalCostFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        // Given
        File file = mock(File.class);
        when(file.getName()).thenReturn(thermalCostsFileName + ".xlsx");
        when(file.getPath()).thenReturn(thermalCostsPath);
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        String horizon = "2025";

        // When
        thermalFileProcessorService.processThermalFile(file, horizon, thermalFileProcessorService::buildThermalCosts, TrajectoryType.THERMAL_COST);

        // Then
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void processThermalCostFile_whenTrajectoryDoesNotExist() throws IOException {
        // Given
        File file = mock(File.class);
        when(file.getName()).thenReturn(thermalCostsFileName + ".xlsx");
        when(file.getPath()).thenReturn(thermalCostsPath);
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());
        String horizon = "2025";

        // When
        thermalFileProcessorService.processThermalFile(file, horizon, thermalFileProcessorService::buildThermalParameters, TrajectoryType.THERMAL_PARAMETER);

        // Then
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void buildThermalParameters() throws IOException {
        // Given
        File file = new File(thermalParametersPath);

        // When
        List<ThermalParameterEntity> thermalParameters = thermalFileProcessorService.buildThermalParameters(file);

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
