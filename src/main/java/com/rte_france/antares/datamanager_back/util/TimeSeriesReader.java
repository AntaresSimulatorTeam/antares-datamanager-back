package com.rte_france.antares.datamanager_back.util;

import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.io.LocalInputFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

public class TimeSeriesReader {
  public static TimeSeriesMatrix readFromParquet(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);

    var inputFile = new LocalInputFile(filePath);
    try (var reader = AvroParquetReader.<TimeSeriesMatrix>builder(inputFile).build()) {
      var matrix = reader.read();
      if (matrix == null) {
        throw new IOException("The Parquet file is empty or does not contain a TimeSeriesMatrix");
      }
      return matrix;
    }
  }
}
