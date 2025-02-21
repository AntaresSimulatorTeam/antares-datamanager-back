/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.antares.timeseries_manager.main;

import com.rte_france.antares.timeseries_manager.structures.TimeSeriesMatrix;
import com.rte_france.antares.timeseries_manager.structures.TimeSeriesMatrixColumn;
import com.rte_france.antares.timeseries_manager.util.Utils;
import org.apache.arrow.compression.CommonsCompressionFactory;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.compression.CompressionUtil;
import org.apache.arrow.vector.ipc.ArrowFileWriter;
import org.apache.arrow.vector.ipc.message.IpcOption;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ArrowTSWriter {
  private static Field doubleField(String name) {
    return new Field(name, FieldType.notNullable(new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE)), null);
  }

  private static Schema createSchema(TimeSeriesMatrix matrix) {
    var fields = matrix.columns().stream()
            .map(column -> doubleField(column.name()))
            .collect(Collectors.toList());
    return new Schema(fields);
  }

  private static void populateDoubleVector(VectorSchemaRoot table, TimeSeriesMatrixColumn column) {
    var vector = table.getVector(column.name());
    var values = column.values();
    var size = values.length;
    switch (vector) {
      case Float8Vector f8Vector -> {
        f8Vector.allocateNew(size);
        table.setRowCount(size);
        IntStream.range(0, size).forEach(i -> f8Vector.set(i, values[i]));
      }
      default -> throw new IllegalStateException();
    }
  }

  public void write(TimeSeriesMatrix matrix, Path outputPath) throws IOException {
    Objects.requireNonNull(matrix);
    Objects.requireNonNull(outputPath);
    outputPath = Utils.ensureExtension(outputPath, this::getDefaultFileExtension);

    var schema = createSchema(matrix);
    try (var allocator = new RootAllocator();
         var table = VectorSchemaRoot.create(schema, allocator)) {
      matrix.columns().forEach(c -> populateDoubleVector(table, c));

      var compressionFactory = new CommonsCompressionFactory();

      try (var out = Files.newOutputStream(outputPath);
           var ch = Channels.newChannel(out);
           var writer = new ArrowFileWriter(table, null, ch, null, IpcOption.DEFAULT, compressionFactory, CompressionUtil.CodecType.ZSTD)) {
        writer.start();
        writer.writeBatch();
        writer.end();
      }
    }
  }

  public String getDefaultFileExtension() {
    return "arrow";
  }
}