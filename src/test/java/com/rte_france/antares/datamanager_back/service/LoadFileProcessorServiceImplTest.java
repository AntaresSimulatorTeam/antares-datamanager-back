package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.dto.UserInfoDto;
import com.rte_france.antares.datamanager_back.repository.LoadRepository;
import com.rte_france.antares.datamanager_back.repository.TrajectoryRepository;
import com.rte_france.antares.datamanager_back.repository.model.TrajectoryEntity;
import com.rte_france.antares.datamanager_back.service.impl.LoadFileProcessorServiceImpl;
import com.rte_france.antares.datamanager_back.service.impl.NasFileService;
import com.rte_france.antares.datamanager_back.service.impl.UserService;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoadFileProcessorServiceImplTest {
    @InjectMocks
    private LoadFileProcessorServiceImpl loadFileProcessorService;

    @Mock
    private TrajectoryRepository trajectoryRepository;

    @Mock
    private LoadRepository loadRepository;

    @Mock
    private TimeSeriesReader timeSeriesReader;

    @Mock
    private TimeSeriesWriter timeSeriesWriter;

    @Mock
    private TimeSeriesMatrix timeSeriesMatrix;

    @Mock
    private NasFileService nasFileService;

    @Mock
    private UserService userService;

  @TempDir
  private Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void processLoadFile_whenTrajectoryExistsAndVersionIsValid() throws IOException {
        var tempFile = tempDir.resolve("test-path.txt");
        Files.createFile(tempFile);
        Files.writeString(tempFile, "This is the content to be written to the file.");
        var trajectoryEntity = new TrajectoryEntity();
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonOrderByVersionDesc(anyString(), anyString())).thenReturn(Optional.of(trajectoryEntity));
        when(timeSeriesReader.readFromTxt(any(Path.class))).thenReturn(timeSeriesMatrix);
        when(timeSeriesWriter.writeToByteArray(any(TimeSeriesMatrix.class))).thenReturn(new byte[0]);

        assertDoesNotThrow(() -> loadFileProcessorService.saveMatrixToNas(tempFile));

        verify(timeSeriesReader, times(1)).readFromTxt(any(Path.class));
        verify(timeSeriesWriter, times(1)).writeToByteArray(any(TimeSeriesMatrix.class));
        verify(nasFileService, times(1)).saveFile(anyString(), any(byte[].class));
    }

    @Test
    void processLoadFile_whenTrajectoryDoesNotExist() throws IOException {
        var tempFile = tempDir.resolve("test-path.txt");
        Files.createFile(tempFile);
        Files.writeString(tempFile, "This is the content to be written to the file.");
        when(userService.getCurrentUserDetails()).thenReturn(UserInfoDto.builder().nni("CF001").build());
        when(trajectoryRepository.findFirstByFileNameAndHorizonOrderByVersionDesc(anyString(), anyString())).thenReturn(Optional.empty());
        when(timeSeriesReader.readFromTxt(any(Path.class))).thenReturn(timeSeriesMatrix);
        when(timeSeriesWriter.writeToByteArray(any(TimeSeriesMatrix.class))).thenReturn(new byte[0]);

        assertDoesNotThrow(() -> loadFileProcessorService.saveMatrixToNas(tempFile));

        verify(timeSeriesReader, times(1)).readFromTxt(any(Path.class));
        verify(timeSeriesWriter, times(1)).writeToByteArray(any(TimeSeriesMatrix.class));
        verify(nasFileService, times(1)).saveFile(anyString(), any(byte[].class));
    }


}