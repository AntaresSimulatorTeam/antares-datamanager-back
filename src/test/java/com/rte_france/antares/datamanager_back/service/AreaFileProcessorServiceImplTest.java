package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.TrajectoryType;
import com.rte_france.antares.datamanager_back.repository.AreaConfigRepository;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.AreaFileProcessorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AreaFileProcessorServiceImplTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private AreaConfigRepository areaConfigRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @InjectMocks
    private AreaFileProcessorServiceImpl areaFileProcessorService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processAreaFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn("src/test/resources/area/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(file.getName()).thenReturn("testFile.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));

        areaFileProcessorService.processAreaFile(file, "2030-2031");

        verify(trajectoryRepository, times(1)).save(any());
        verify(areaConfigRepository, times(1)).saveAll(any());
    }

    @Test
    void processAreaFile_whenTrajectoryDoesNotExist() throws IOException {
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn("src/test/resources/area/testFile.xlsx");

        when(file.getName()).thenReturn("testFile.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());

        areaFileProcessorService.processAreaFile(file, "2030-2031");

        verify(trajectoryRepository, times(1)).save(any());
        verify(areaConfigRepository, times(1)).saveAll(any());
    }
}
