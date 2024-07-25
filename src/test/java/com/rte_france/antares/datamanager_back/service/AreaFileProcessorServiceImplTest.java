package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.AreaConfigRepository;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.AreaFileProcessorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static com.rte_france.antares.datamanager_back.util.Utils.checkTrajectoryVersion;
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

    @Disabled
    @Test
    void processAreaFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        File file = mock(File.class);
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(file.getName()).thenReturn("testFile.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        when(checkTrajectoryVersion(file, trajectoryEntity)).thenReturn(true);

        areaFileProcessorService.processAreaFile(file);

        verify(trajectoryRepository, times(1)).findFirstByFileNameOrderByVersionDesc(any());
        verify(areaFileProcessorService, times(1)).saveTrajectory(any(), any());
    }

    @Disabled
    @Test
    void processAreaFile_whenTrajectoryDoesNotExist() throws IOException {
        File file = mock(File.class);

        when(file.getName()).thenReturn("testFile");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());

        areaFileProcessorService.processAreaFile(file);

        verify(trajectoryRepository, times(1)).findFirstByFileNameOrderByVersionDesc(any());
        verify(areaFileProcessorService, times(1)).saveTrajectory(any(), any());
    }

    @Disabled
    @Test
    void processAreaFile_whenTrajectoryExistsButVersionIsInvalid() throws IOException {
        File file = mock(File.class);
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(file.getName()).thenReturn("testFile");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        when(checkTrajectoryVersion(file, trajectoryEntity)).thenReturn(false);

        areaFileProcessorService.processAreaFile(file);

        verify(trajectoryRepository, times(1)).findFirstByFileNameOrderByVersionDesc(any());
        verify(areaFileProcessorService, times(1)).saveTrajectory(any(), any());
    }
}
