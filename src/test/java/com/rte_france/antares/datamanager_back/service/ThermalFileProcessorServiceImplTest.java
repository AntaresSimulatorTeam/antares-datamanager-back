package com.rte_france.antares.datamanager_back.service;



import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.ThermalCostTypeRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;

import com.rte_france.antares.datamanager_back.repository.model.ThermalParameterEntity;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
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
        Mockito.when(file.getPath()).thenReturn("src/test/resources/thermal_capacity/thermal_BE_PEMMDB23_26avril.xlsx");
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(file.getName()).thenReturn("thermal_BE_PEMMDB23_26avril.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));

        thermalFileProcessorService.processThermalCapacityFile(file,"2023-2024");

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processThermalCapacityFile_whenTrajectoryDoesNotExist() throws IOException {
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn("src/test/resources/thermal_capacity/thermal_BE_PEMMDB23_26avril.xlsx");
        when(file.getName()).thenReturn("thermal_BE_PEMMDB23_26avril.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());

        thermalFileProcessorService.processThermalCapacityFile(file,"2023-2024");

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processThermalParameterFile() throws IOException {
        // Given

        Path filePath = Paths.get("src/test/resources/thermal_parameters/common_param_BP23_A_ref.xlsx");
        File file = filePath.toFile();

        String horizon = "2025";

        TrajectoryEntity expectedTrajectory = TrajectoryEntity.builder()
                .fileName("common_param_BP23_A_ref")
                .type(TrajectoryType.THERMAL_PARAMETER.name())
                .version(1)
                .horizon(horizon)
                .build();


        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc("common_param_BP23_A_ref.xlsx"))
                .thenReturn(Optional.of(expectedTrajectory));
        when(trajectoryRepository.save(any())).thenReturn(expectedTrajectory);

        // When
        thermalFileProcessorService.processThermalParameterFile(file, horizon);

        // Then
        verify(trajectoryRepository, times(1))
                .findFirstByFileNameOrderByVersionDesc("common_param_BP23_A_ref");

        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));

    }

    @Test
    void processThermalCostFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        // Given
        File file = mock(File.class);
        when(file.getName()).thenReturn("costs_BP23_A_ref.xlsx");
        when(file.getPath()).thenReturn("src/test/resources/thermal_cost/costs_BP23_A_ref.xlsx");
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        String horizon = "2025";

        // When
        thermalFileProcessorService.processThermalCostFile(file, horizon);

        // Then
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void processThermalCostFile_whenTrajectoryDoesNotExist() throws IOException {
        // Given
        File file = mock(File.class);
        when(file.getName()).thenReturn("costs_BP23_A_ref.xlsx");
        when(file.getPath()).thenReturn("src/test/resources/thermal_cost/costs_BP23_A_ref.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());
        String horizon = "2025";

        // When
        thermalFileProcessorService.processThermalCostFile(file, horizon);

        // Then
        verify(trajectoryRepository, times(1)).save(any(TrajectoryEntity.class));
    }

    @Test
    void buildThermalParameters() throws IOException {
        // Given
        File file = new File("src/test/resources/thermal_parameters/common_param_BP23_A_ref.xlsx");

        // When
        List<ThermalParameterEntity> thermalParameters = thermalFileProcessorService.buildThermalParameters(file);

        // Then
        assertEquals(47, thermalParameters.size());
    }
}
