package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.repository.AreaConfigRepository;
import com.rte_france.antares.datamanager_back.repository.AreaRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.AreaFileProcessorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
    void processAreaFile_whenSameFileWithSameContent() throws IOException {
        File file = new File("src/test/resources/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        areaFileProcessorService.processAreaFile(file);

        verify(trajectoryRepository, times(1)).findFirstByFileNameOrderByVersionDesc(any());
        verify(trajectoryRepository, times(1)).save(any());
        verify(areaConfigRepository, times(1)).saveAll(anyCollection());
    }

    @Test
    void processAreaFile_whenSameFileWithDifferentContent() throws IOException {
        File file = new File("src/test/resources/testFile.xlsx");
        TrajectoryEntity trajectoryEntity = TrajectoryEntity.builder()
                .fileSize(522L)
                .fileName("testFile")
                .checksum("ddcf3b936326b35bf74caaecb2cb24cfd96f49b6472d1e6bc19c8eccb7a5c51b")
                .build();

        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));
        areaFileProcessorService.processAreaFile(file);

        verify(trajectoryRepository, times(1)).findFirstByFileNameOrderByVersionDesc(any());
        verify(areaConfigRepository, times(1)).saveAll(anyCollection());
    }

}
