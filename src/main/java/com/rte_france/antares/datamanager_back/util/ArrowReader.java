package com.rte_france.antares.datamanager_back.util;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.types.pojo.Field;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ArrowReader {
  private static final BufferAllocator ALLOCATOR = new RootAllocator();

  public static Matrix readMatrixFromArrow(Path filePath) throws IOException {
    Objects.requireNonNull(filePath);

    try (var channel = Files.newByteChannel(filePath);
         var reader = new ArrowFileReader(channel, ALLOCATOR)) {

      reader.loadNextBatch();
      var root = reader.getVectorSchemaRoot();
      List<Field> fields = root.getSchema().getFields();

      var columns = new ArrayList<MatrixColumn>();
      for (var field : fields) {
        var vector = root.getVector(field.getName());
        var values = new double[vector.getValueCount()];
        for (var i = 0; i < vector.getValueCount(); i++) {
          values[i] = ((Float8Vector) vector).get(i);
        }
        columns.add(new MatrixColumn(field.getName(), values));
      }

      return new Matrix(columns);
    }
  }

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