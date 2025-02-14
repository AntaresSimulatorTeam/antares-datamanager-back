/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.timeseries_manager.main;

import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.io.LocalInputFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;


public final class AvroTSReader implements TimeSeriesReader<TimeSeriesMatrix> {
  @Override
  public TimeSeriesMatrix read(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);
    if (Files.notExists(filePath)) {
      throw new IllegalArgumentException("File " + filePath + " doesn't exist");
    }

    var inputFile = new LocalInputFile(filePath);
    try (var reader = AvroParquetReader.<TimeSeriesMatrix>builder(inputFile).build()) {
      var matrix = reader.read();
      if (matrix == null) {
        throw new IOException("The Parquet file is empty or does not contain a TimeSeriesMatrix");
      }
      return matrix;
    }
  }

  @Override
  public TimeSeriesMatrix readFromTxt(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);
    try (var lines = Files.lines(filePath)) {
      var rows = lines.map(line -> {
        var values = line.trim().split("\\s+");
        var doubles = Arrays.stream(values)
                .map(Double::parseDouble)
                .toList();
        return new TimeSeriesRow(doubles);
      }).collect(Collectors.toList());
      return new TimeSeriesMatrix(rows);
    }
  }
}