package com.rte_france.antares.datamanager_back.util;

import org.apache.parquet.hadoop.ParquetReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ParquetTimeSeriesReader {
  public static Matrix readFromParquet(org.apache.hadoop.fs.Path filePath) throws IOException {
    try (var reader = ParquetReader.builder(new MatrixReadSupport(8760), filePath).build()) {
      return reader.read();
    }
  }

  public static Matrix readMatrixFromTxt(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);

    try (var lines = Files.lines(filePath)) {
      var iterator = lines.iterator();
      if (!iterator.hasNext()) {
        throw new IllegalArgumentException("File is empty");
      }

      var firstLine = iterator.next();
      var columnCount = firstLine.split("\\s+").length;
      var data = new ArrayList<List<Double>>(columnCount);
      for (var i = 0; i < columnCount; i++) {
        data.add(new ArrayList<>());
      }

      fillDataList(firstLine, iterator, data);

      var columns = new ArrayList<MatrixColumn>(data.size());
      for (int j = 0; j < data.size(); j++) {
        columns.add(new MatrixColumn("Column" + j, data.get(j).stream().mapToDouble(Double::doubleValue).toArray()));
      }

      return new Matrix(columns);
    }
  }

  private static void fillDataList(String firstLine, Iterator<String> iterator, ArrayList<List<Double>> data) {
    Stream.concat(Stream.of(firstLine), Stream.generate(iterator::next).takeWhile(x -> iterator.hasNext()))
            .map(line -> line.split("\\s+"))
            .forEach(values -> {
              for (var j = 0; j < values.length; j++) {
                data.get(j).add(Double.parseDouble(values[j]));
              }
            });
  }
}