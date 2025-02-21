package com.rte_france.antares.datamanager_back.service.impl;

import com.rte_france.antares.datamanager_back.service.TimeSeriesStorageService;
import com.rte_france.antares.timeseries_manager.main.ArrowTSReader;
import com.rte_france.antares.timeseries_manager.main.ArrowTSWriter;
import com.rte_france.antares.timeseries_manager.structures.TimeSeriesMatrix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public final class TimeSeriesStorageServiceImpl implements TimeSeriesStorageService {
  private final ArrowTSReader reader;
  private final ArrowTSWriter writer;

  @Override
  public TimeSeriesMatrix readTimeSeries(Path filePath) throws IOException {
    return reader.read(filePath);
  }

  @Override
  public void writeTimeSeries(TimeSeriesMatrix matrix, Path outputPath) throws IOException {
    writer.write(matrix, outputPath);
  }
}
