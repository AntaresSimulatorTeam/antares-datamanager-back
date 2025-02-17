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
import java.nio.file.Path;
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
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        TrajectoryEntity trajectoryEntity = mock(TrajectoryEntity.class);

        when(path.getFileName().toString()).thenReturn("links_BP23_A_ref.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.of(trajectoryEntity));

        linkFileProcessorService.processLinkFile(path,"2030-2031");

        verify(trajectoryRepository, times(1)).save(any());
    }

    @Test
    void processLinkFile_whenTrajectoryDoesNotExist() throws IOException {
        Path path = mock(Path.class);
        Mockito.when(path.toString()).thenReturn("src/test/resources/link/links_BP23_A_ref.xlsx");
        when(path.getFileName().toString()).thenReturn("links_BP23_A_ref.xlsx");
        when(trajectoryRepository.findFirstByFileNameOrderByVersionDesc(any())).thenReturn(Optional.empty());

        linkFileProcessorService.processLinkFile(path,"2030-2031");

        verify(trajectoryRepository, times(1)).save(any());
    }
}
