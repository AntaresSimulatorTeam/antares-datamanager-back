/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.rte_france.antares.datamanager_back.util;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.rte_france.antares.datamanager_back.util.ColumnType.FLOAT;
import static com.rte_france.antares.datamanager_back.util.ColumnType.INT;

/**
 * @author Sylvain Leclerc <sylvain.leclerc@rte-france.com>
 */
public class ArrowWriter {

  private static final BufferAllocator ALLOCATOR = new RootAllocator();

  private static Field floatField(String name) {
    return new Field(name, FieldType.notNullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)), null);
  }

  private static Field intField(String name) {
    return new Field(name, FieldType.notNullable(new ArrowType.Int(32, true)), null);
  }

  private static Field createField(MatrixColumn column) {
    return switch (column.type()) {
      case INT -> intField(column.name());
      case FLOAT -> floatField(column.name());
      default -> throw new IllegalArgumentException("Invalid column type " + column.type());
    };
  }

  private static Schema createSchema(Matrix matrix) {
    var fields = matrix.getColumns().stream()
            .map(ArrowWriter::createField)
            .collect(Collectors.toList());
    return new Schema(fields);
  }

  private static void populateFloatVector(VectorSchemaRoot table, MatrixColumn column) {
    var vector = (Float4Vector) table.getVector(column.name());
    var values = column.getFloatValues();
    var size = values.length;
    vector.allocateNew(size);
    table.setRowCount(size);
    IntStream.range(0, size).forEach(i -> vector.set(i, values[i]));
  }

  private static void populateIntVector(VectorSchemaRoot table, MatrixColumn column) {
    var vector = (IntVector) table.getVector(column.name());
    var values = column.getIntValues();
    var size = values.length;
    vector.allocateNew(size);
    table.setRowCount(size);
    IntStream.range(0, size).forEach(i -> vector.set(i, values[i]));
  }

  public void write(Matrix matrix, OutputStream out) throws IOException {
    var schema = createSchema(matrix);
    try (var table = VectorSchemaRoot.create(schema, ALLOCATOR)) {
      matrix.getColumns().forEach(c -> {
        switch (c.type()) {
          case INT -> populateIntVector(table, c);
          case FLOAT -> populateFloatVector(table, c);
          default -> throw new IllegalArgumentException("Invalid column type " + c.type());
        }
      });

      // OpenOption[] options = {StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

      try (var ch = Channels.newChannel(out);
           var writer = new ArrowFileWriter(table, null, ch)) {
        writer.start();
        writer.writeBatch();
        writer.end();
      }
    }
  }

  public String getDefaultFileExtension() {
    return "arrow";
  }

  public static void main(String[] args) {
      var writer = new ArrowWriter();
      writer.write(matrix, Path.of("src/main/resources/test-matrix.arrow"));
  }
}
