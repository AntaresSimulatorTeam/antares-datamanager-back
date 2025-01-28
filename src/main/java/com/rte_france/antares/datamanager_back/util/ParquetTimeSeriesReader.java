package com.rte_france.antares.datamanager_back.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ParquetTimeSeriesReader {
  public static Matrix readMatrixFromTxt(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);

    var lines = Files.readAllLines(filePath);
    if (lines.isEmpty()) {
      throw new IllegalArgumentException("File is empty");
    }

    var rowCount = lines.size();
    var columnCount = lines.getFirst().split("\\s+").length;

    return readMatrix(lines, rowCount, columnCount, Double::parseDouble, (data, value) -> data.add(Double.parseDouble(value)));
  }

  private static Matrix readMatrix(List<String> lines, int rowCount, int columnCount, Function<String, Double> parser, BiConsumer<List<Double>, String> adder) {
    var data = new ArrayList<List<Double>>(columnCount);
    for (var i = 0; i < columnCount; i++) {
      data.add(new ArrayList<>(rowCount));
    }

    for (var line : lines) {
      var values = line.split("\\s+");
      for (var j = 0; j < columnCount; j++) {
        adder.accept(data.get(j), values[j]);
      }
    }

    var columns = new ArrayList<MatrixColumn>(columnCount);
    for (int j = 0; j < columnCount; j++) {
      columns.add(new MatrixColumn("Column" + j, data.get(j).stream().mapToDouble(Double::doubleValue).toArray()));
    }

    return new Matrix(columns);
  }
}