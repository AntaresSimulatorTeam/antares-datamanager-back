package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.ThermalFileProcessorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ThermalFileProcessorServiceImplTest {


    @Mock
    private TrajectoryRepository trajectoryRepository;

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
}
