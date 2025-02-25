package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.datamanager_back.service.impl.TimeSeriesStorageServiceImpl;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesMatrix;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesReader;
import com.rte_france.antares.datamanager_back.util.timeseries_manager.TimeSeriesWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class TimeSeriesStorageServiceImplTest {

  @InjectMocks
  private TimeSeriesStorageServiceImpl timeSeriesStorageService;

  @Mock
  private TimeSeriesReader timeSeriesReader;

  @Mock
  private TimeSeriesWriter timeSeriesWriter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void readTimeSeries_validFile() throws IOException {
    var filePath = Path.of("validFile.arrow");
    var expectedMatrix = mock(TimeSeriesMatrix.class);

    when(timeSeriesReader.read(filePath)).thenReturn(expectedMatrix);

    var result = timeSeriesStorageService.readTimeSeries(filePath);

    assertNotNull(result);
    verify(timeSeriesReader, times(1)).read(filePath);
  }

  @Test
  void writeTimeSeries_validMatrix() throws IOException {
    var matrix = mock(TimeSeriesMatrix.class);
    var outputPath = Path.of("outputFile.arrow");

    doNothing().when(timeSeriesWriter).write(matrix, outputPath);

    assertDoesNotThrow(() -> timeSeriesStorageService.writeTimeSeries(matrix, outputPath));
    verify(timeSeriesWriter, times(1)).write(matrix, outputPath);
  }
}