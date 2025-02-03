package com.rte_france.antares.datamanager_back.util;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.types.pojo.Field;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ArrowReader {
  public static Matrix readMatrixFromArrow(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);

    try (var channel = Files.newByteChannel(filePath);
         var allocator = new RootAllocator();
         var reader = new ArrowFileReader(channel, allocator)) {

      reader.loadNextBatch();
      var root = reader.getVectorSchemaRoot();
      var fields = root.getSchema().getFields();

      var columns = new ArrayList<MatrixColumn>();
      fillMatrixColumns(fields, root, columns);

      return new Matrix(columns);
    }
  }

  private static void fillMatrixColumns(List<Field> fields, VectorSchemaRoot root, ArrayList<MatrixColumn> columns) {
    for (var field : fields) {
      var vector = root.getVector(field.getName());
      var values = new double[vector.getValueCount()];
      for (var i = 0; i < vector.getValueCount(); i++) {
        switch (vector) {
          case Float8Vector f -> values[i] = f.get(i);
          default -> throw new IllegalStateException();
        }
      }
      columns.add(new MatrixColumn(field.getName(), values));
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

  private static void fillDataList(String firstLine, Iterator<String> iterator, List<List<Double>> data) {
    Stream.concat(Stream.of(firstLine), Stream.generate(iterator::next).takeWhile(x -> iterator.hasNext()))
            .map(line -> line.split("\\s+"))
            .forEach(values -> {
              for (var j = 0; j < values.length; j++) {
                data.get(j).add(Double.parseDouble(values[j]));
              }
            });
  }
}