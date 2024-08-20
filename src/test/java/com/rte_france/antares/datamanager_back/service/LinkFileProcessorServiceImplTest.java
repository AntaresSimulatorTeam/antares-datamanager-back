package com.rte_france.antares.datamanager_back.service;


import com.rte_france.antares.datamanager_back.repository.LinkRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.LinkFileProcessorServiceImpl;
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

class LinkFileProcessorServiceImplTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @InjectMocks
    private LinkFileProcessorServiceImpl linkFileProcessorService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processLinkFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(file.getName()).thenReturn("links_BP23_A_ref.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));

        linkFileProcessorService.processLinkFile(file);

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processLinkFile_whenTrajectoryDoesNotExist() throws IOException {
        File file = mock(File.class);
        Mockito.when(file.getPath()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        when(file.getName()).thenReturn("links_BP23_A_ref.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());

        linkFileProcessorService.processLinkFile(file);

        verify(trajectoryRepository, times(1)).save(any());
    }
}
