package com.rte_france.antares.datamanager_back.service;

import com.rte_france.antares.timeseries_manager.structures.TimeSeriesMatrix;

import java.io.IOException;
import java.nio.file.Path;

public interface TimeSeriesStorageService {
  TimeSeriesMatrix readTimeSeries(Path filePath) throws IOException;
  void writeTimeSeries(TimeSeriesMatrix matrix, Path outputPath) throws IOException;
}
